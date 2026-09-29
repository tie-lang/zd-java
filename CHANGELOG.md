# zd-java Changelog / 变更日志

Reverse chronological. / 倒序排列。

## [0.2.0] Graph columnar carriage + nullable node payloads + repo scaffolding / 图容器列式承载 + 节点载荷可空 + 仓内脚手架 (2026-09-29)

* `ZdGraph` 图容器新增**列式承载**（设计案 §6「大图的节点表与边表应当用列式容器 + 编码族承载
  —— id 列用 delta、标签列用字典」）：声明字节 bit2 标记形态，`encodeColumnar` 产出
  `[声明][varint 列式容器长度][列式容器 7 列][varint 载荷区长度][载荷区]`；`decode` 按声明位 2
  **自动分派**两形态，语义等价。`encodeColumnarExt` 提供 ext 子类型（0x47）形态 /
  `ZdGraph` gains the **columnar carriage** (design §6): declaration bit 2 marks the form;
  `encodeColumnar` emits `[decl][varint columnar length][7-column container][varint payload
  length][payload area]`; `decode` auto-dispatches on bit 2 and the two forms are
  semantically equivalent. `encodeColumnarExt` is the ext-subtype (0x47) form
* 列式 7 列（固定序）：`node_id` / `edge_from` / `edge_to` / `edge_label` / `edge_weight` /
  `edge_dir_ovr` / `edge_head`。**列自动择优**（纯表示层）——全同值列 → RLE、非降序整型列 →
  delta、标签列 → 字典；纯结构图的权重（全 0.0）/ 覆盖（全 -1）/ 头列因此从 8 或 1 字节/边
  降到单游程 /
  Seven columns (fixed order): `node_id` / `edge_from` / `edge_to` / `edge_label` /
  `edge_weight` / `edge_dir_ovr` / `edge_head`, with **per-column auto-pick** (a pure
  representation layer) — all-equal → RLE, non-descending integer → delta, label →
  dictionary. This shrinks the weight (all 0.0) / override (all -1) / head columns of a
  pure-structure graph from 8 or 1 byte per edge to a single run
* 实测收益（2000 节点 / 4000 边，列式 ÷ 紧凑）：纯结构 **2.8×**、变权重 **1.7×**、含载荷属性
  **1.3×**；载荷区（任意 zd 值）仍走自定界 wire2 子树，是含载荷图的体积大头 /
  Measured gain (2000 nodes / 4000 edges, columnar vs compact): pure structure **2.8×**,
  varying weights **1.7×**, with payloads + attributes **1.3×**. The payload area (any zd
  value) still rides self-delimiting wire2 subtrees — the bulk of payload-heavy graphs
* **破坏性**：紧凑形态的节点载荷改为**可空**——`节点 = [id][载荷标志 1B][载荷?][属性标志 1B]
  [属性?]`（原布局无条件写载荷，无法表达「无载荷节点」，与列式形态的稀疏载荷区不等价）。
  0.1.0 无外部消费者，此番为规范澄清；两形态从此语义完全对齐 /
  **Breaking**: the compact form's node payload became **nullable** —
  `node = [id][payload flag][payload?][attr flag][attrs?]` (the old layout always wrote a
  payload, unable to express a payload-less node, and thus diverged from the columnar
  form's sparse payload area). 0.1.0 has no external consumers; this is a spec
  clarification, and the two forms now align exactly
* 修复两处缺陷（均由探针抓出）：① 空列的 delta 编码/解码越界（空图的 `node_id` 列）；② 列式
  形态分派分支缺尾部字节校验（多段文档里段尾多余字节会被静默忽略）/
  Two defects fixed (both caught by the probe): (1) delta encode/decode on an empty
  column (the `node_id` column of an empty graph); (2) the columnar dispatch branch
  lacked the trailing-bytes check (excess bytes after a segment payload were silently
  ignored)
* 仓内脚手架：Gradle wrapper（`./gradlew probe`，免全局 Gradle）；发布配置（pom `release`
  profile：source/javadoc jar + GPG 签名 + Sonatype Central Portal 上传，
  `autoPublish=false` 留人工闸）+ [docs/publishing.md](docs/publishing.md) 发布指南 /
  Repo scaffolding: a Gradle wrapper (`./gradlew probe`, no global Gradle needed) and the
  publishing setup (the pom `release` profile: source/javadoc jars + GPG signing +
  Central Portal upload with `autoPublish=false` as a manual gate) plus
  [docs/publishing.md](docs/publishing.md)
* `ZdProbe` 121 checks 全绿（新增 18 条图容器列式检查：两形态往返与语义等价、稀疏载荷/属性、
  自动择优、id 非升序回退、string id 拒绝、空图、尾部字节拒绝、体积收益） /
  `ZdProbe` 121 checks green (18 new graph-columnar checks: round-trips and semantic
  equivalence of both forms, sparse payloads/attrs, auto-picking, non-ascending id
  fallback, string-id rejection, the empty graph, trailing-byte rejection, size gain)

## [0.1.0] zd v3 full support — initial release / zd v3 完整支持——首发 (2026-09-28)

* zd v3 载体全量：10 字节头（写方缺省 v3 `"03"`、v2 兼容路径、写侧禁写 v1；v3 flags
  低 7 位含 bit5 索引 footer / bit6 图容器提示）+ 25 字节索引 footer（`ZD3FT` +
  index_off/index_len u64 BE + crc32 IEEE；段表 varint + 类型 0-6 + varint 长度钉名，
  段类型 6 名称必填）；footer 存在时消费方必须经索引定位段，头 bit5 与 footer 存在性
  不一致确定性拒绝 / Full zd v3 carrier: the 10-byte header (v3 default `"03"`, v2
  compat path, v1 writes forbidden; v3 flags low-7 bits with bit5 index footer /
  bit6 graph hint) + the 25-byte index footer (`ZD3FT` + u64 BE offsets + IEEE crc32;
  varint segment table, types 0-6, varint-length-pinned names, type-6 name required);
  footered documents must be read via the index; header-bit5/footer disagreement is
  deterministically rejected
* 行级 wire2 记录层（六字段定序 tags 10/18/26/34/42/50）+ 树值模型 `ZdNode`（kind 0-3，
  与 tie td 表同构）+ DFS 先序平铺/逆推 `ZdTrees` / The wire2 record layer (six fixed
  fields, tags 10/18/26/34/42/50) + the tree value model `ZdNode` (kinds 0-3,
  isomorphic to the tie td table) + DFS preorder flatten/reconstruct `ZdTrees`
* 列式编码族：容器 `0xD6 | ncols | 每列 [类型][编码] | 各列`；编码 0 plain / 1 RLE
  （游程 ≥ 1）/ 2 delta（仅整型列，差分非负，负差分写侧确定性拒绝）/ 3 字典（仅字符串列，
  首见序字典）；v2 形态（无编码声明）解码兼容 / The columnar encoding family: container
  `0xD6 | ncols | per column [type][encoding] | values`; encodings 0 plain / 1 RLE
  (runs ≥ 1) / 2 delta (int columns, non-negative diffs — negative diffs deterministically
  rejected on write) / 3 dictionary (string columns, first-seen-order dict); the v2 form
  (no encoding declaration) decodes compatibly
* schema 演进：字段三态 active → deprecated → removed（跃迁纪律 API 强制）、字段号永不
  复用（removed 编号作废）、deprecated/removed 写侧禁止；schema_id = tsha1f(n=48,
  base-48) 内容指纹——**tsha1f Java 移植与 tiec `tests/tsha_probe/gen_tsha1_core.py`
  逐位一致**（35 条 KAT 钉入探针，向量清单 `scripts/tsha1f-kat.txt`）/ Schema evolution:
  three field states with API-enforced transitions, field numbers never reused,
  deprecated/removed write-forbidden; schema_id = the tsha1f (n=48, base-48) content
  fingerprint — **the tsha1f Java port is bit-identical to tiec
  `tests/tsha_probe/gen_tsha1_core.py`** (35 KATs pinned in the probe, table at
  `scripts/tsha1f-kat.txt`)
* 标准类型三件（ext 固定标记，v3 强制支持）：timestamp（1：8B 纪元秒 i64 BE + 4B 纳秒
  u32 BE）、decimal（2：符号 1B + 数字串 UTF-8 + 指数 i8）、uuid（3：16B RFC 4122）/
  The three standard types (fixed ext markers, mandatory for v3): timestamp (1), decimal
  (2), uuid (3)
* 图容器：段类型 4 + ext 子类型（标记 0x47 'G'）；声明字节（bit0 有向 / bit1 节点 id
  形态 varint|string）+ 节点/边表；边头 bit0 有向覆盖（异或生效）/ bit1 标签 / bit2 权重
  f64 BE / bit3 属性；节点 id 唯一、边引用双侧校验、自环/多重边/空图合法；节点载荷 =
  任意 zd 值（自定界 wire2 子树嵌入） / The graph container: segment type 4 + ext
  subtype (marker 0x47 'G'); declaration byte (bit0 directed / bit1 node-id form) +
  node/edge tables; edge-head bits 0 directed-override (XOR) / 1 label / 2 weight f64 BE
  / 3 attributes; node ids unique, edge references validated on both sides, self-loops /
  multi-edges / the empty graph legal; node payloads = any zd value (self-delimiting
  wire2 subtree embedding)
* 多段文档 `ZdSegments`：组装（段自动置位 bit0/1/4/6 + bit5 恒置）与经索引读取；压缩
  载荷段形态 = `varint 变体 + 数据`（0 store / 1 zstd / 2 lz4，无内嵌算法、解压器可插拔，
  未知变体确定性拒绝）；字符串池 + 字典引用（权威形态 `0xC8 + varint 序号`） /
  Multi-segment documents `ZdSegments`: assembly (segments auto-set bits 0/1/4/6, bit5
  always) and index-based reads; the compressed-segment form = `varint variant + data`
  (0 store / 1 zstd / 2 lz4 — no embedded algorithms, pluggable decompressor, unknown
  variants deterministically rejected); the string pool + dictionary references (the
  authoritative `0xC8 + varint index` form)
* `ZdProbe` 103 checks 全绿（纯 JVM 确定性探针：头 / footer / 行树往返 / 列式 / 池 /
  schema / 标准类型 / 图 / 多段 / tsha1f KAT） / `ZdProbe` 103 checks all green (the
  pure-JVM deterministic probe: header / footer / row-tree round-trips / columnar /
  pool / schema / standard types / graph / segments / tsha1f KATs)
* 许可证：Tie Public License 2.2（TPL 2.2）© TIE-LANG organization / License: the Tie
  Public License 2.2 (TPL 2.2) © TIE-LANG organization
