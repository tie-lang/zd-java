# zd-java Changelog / 变更日志

Reverse chronological. / 倒序排列。

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
