# zd-java

zd（tie 生态二进制序列化格式）编解码库 —— 纯 Java（JDK 17+，零依赖，包名
`org.tielang.zd`）。对齐语言规范 §17.2 与 zd v3 设计案
（`2026-09-28-zd-v3-design.md`）：**v3 完整支持**——写方缺省 v3、读取义务 v2+v3、
索引 footer、列式编码族、schema 演进（schema_id = tsha1f 指纹）、标准类型三件
（timestamp / decimal / uuid）、图容器（段类型 4 + ext 子类型 0x47）。

zd-java: the codec library for zd, the tie-ecosystem binary serialization format —
pure Java (JDK 17+, zero dependencies, package `org.tielang.zd`). Aligned with
language-spec §17.2 and the zd v3 design (`2026-09-28-zd-v3-design.md`): **full v3
support** — v3 default writes, the v2+v3 reading obligation, the index footer, the
columnar encoding family, schema evolution (schema_id = the tsha1f fingerprint), the
three standard types (timestamp / decimal / uuid), and the graph container (segment
type 4 + ext subtype 0x47).

## 格式速览 / Format at a Glance

* 头（10 字节）：魔数 `TIEDBZD` + base-48 版本两位（v2 = `00 02`、v3 = `00 03`）+ flags；
  v3 flags 低 7 位（bit5 索引 footer、bit6 图容器提示）
* 索引 footer（25 字节）：`ZD3FT` + index_off/index_len（u64 BE）+ crc32（IEEE）；
  段表 = varint 段数 + 各段 `[类型 varint][偏移 u64 BE][长度 u64 BE][名称 varint 前缀 UTF-8]`，
  段类型 0 数据体 / 1 字符串池 / 2 列式 / 3 schema / 4 图 / 5 压缩 / 6 自定义（名称必填）
* 列式编码族（列级声明、纯表示层）：0 plain、1 RLE、2 delta（仅整型列，差分非负）、
  3 字典（仅字符串列）
* schema 演进：字段三态 active → deprecated → removed，字段号永不复用；
  schema_id = tsha1f(n=48, base-48) 内容指纹（Java 移植与 tiec
  `tests/tsha_probe/gen_tsha1_core.py` 逐位一致，KAT 钉入 `ZdProbe`）
* 标准类型（ext 固定标记）：timestamp = 1（8B 纪元秒 i64 BE + 4B 纳秒 u32 BE）、
  decimal = 2（符号 1B + 数字串 UTF-8 + 指数 i8）、uuid = 3（16B RFC 4122）
* 图容器：`[声明 1B][节点数][节点表][边数][边表]`，或**列式承载**（声明位 2）；声明字节
  bit0 有向 / bit1 节点 id 形态（varint 索引 | string 显式 id）/ bit2 列式承载；节点 =
  `[id][载荷标志][载荷?][属性标志][属性?]`；边头 bit0 有向覆盖（与容器声明异或）、bit1 标签、
  bit2 权重、bit3 属性；节点 id 唯一、边引用必须存在、自环/多重边/空图合法
* **列式承载**（大图，设计案 §6「id 列用 delta、标签列用字典」）：`encodeColumnar` 自动择优
  —— 全同值列 → RLE、非降序整型列 → delta、标签列 → 字典；`decode` 按声明位 2 自动分派，
  两形态语义等价。实测收益（2 千节点 / 4 千边）：纯结构 **2.8×**、含权重 **1.7×**、含载荷
  属性 **1.3×**

* Header (10 bytes): the `TIEDBZD` magic + two base-48 version digits (v2 = `00 02`,
  v3 = `00 03`) + flags; v3 flags use the low 7 bits (bit5 index footer, bit6 graph hint)
* Index footer (25 bytes): `ZD3FT` + index_off/index_len (u64 BE) + crc32 (IEEE);
  the segment table = varint count + per segment `[type varint][offset u64 BE]
  [length u64 BE][name varint-prefixed UTF-8]`, types 0 data / 1 string pool /
  2 columnar / 3 schema / 4 graph / 5 compressed / 6 custom (name required)
* Columnar encoding family (column-level declarations, a pure representation layer):
  0 plain, 1 RLE, 2 delta (int columns only, non-negative diffs), 3 dictionary
  (string columns only)
* Schema evolution: three field states active → deprecated → removed, field numbers
  never reused; schema_id = the tsha1f (n=48, base-48) content fingerprint (the Java
  port is bit-identical to tiec `tests/tsha_probe/gen_tsha1_core.py`; KATs pinned in
  `ZdProbe`)
* Standard types (fixed ext markers): timestamp = 1 (8B epoch-seconds i64 BE + 4B
  nanoseconds u32 BE), decimal = 2 (1B sign + digit string UTF-8 + i8 exponent),
  uuid = 3 (16B RFC 4122)
* Graph container: the compact form `[decl 1B][node count][node table][edge count]
  [edge table]` or the **columnar carriage** (declaration bit 2); declaration bits are
  0 directed, 1 node-id form (varint index | explicit string), 2 columnar; a node =
  `[id][payload flag][payload?][attr flag][attrs?]`; edge-head bits are 0
  directed-override (XOR with the container declaration), 1 label, 2 weight,
  3 attributes; node ids unique, edge endpoints must exist, self-loops / multi-edges /
  the empty graph legal
* **Columnar carriage** (large graphs, design §6 "delta for the id column, dictionary
  for the label column"): `encodeColumnar` auto-picks — all-equal columns → RLE,
  non-descending integer columns → delta, the label column → dictionary; `decode`
  dispatches on declaration bit 2 and the two forms are semantically equivalent.
  Measured gain (2000 nodes / 4000 edges): pure structure **2.8×**, with weights
  **1.7×**, with payloads + attributes **1.3×**

## 入门 / Getting Started

```java
import org.tielang.zd.*;

// 行级往返 / row-level round-trip
List<ZdRow> rows = List.of(new ZdRow(2, "count", 42L, 0.0, "", 0));
byte[] doc = ZdDocWriter.write(0, rows);              // v3（缺省） / v3 (default)
byte[] v2 = ZdDocWriter.writeV2(0, rows);             // v2 兼容路径 / v2 compat
byte[] indexed = ZdDocWriter.writeIndexed(0, rows);   // 带索引 footer / with index footer
List<ZdRow> back = ZdVolume.readRows(indexed);        // v2 + v3 读义务 / v2 + v3 reading

// 树级 / tree level
ZdNode.Table root = ZdNode.Table.builder().put("name", "zd").build();
byte[] tree = ZdDocWriter.writeTree(0, root);
ZdNode back2 = ZdVolume.readTree(tree);

// 图容器（紧凑形态 / 列式承载，decode 自动分派） / graph container (both forms)
byte[] g = ZdGraph.encode(graph);                      // 紧凑逐条 / compact
byte[] gc = ZdGraph.encodeColumnar(graph);             // 列式承载（大图）/ columnar (large)
byte[] gx = ZdGraph.encodeColumnarExt(graph);          // ext 子类型 0x47 / ext subtype
ZdGraph.GraphData backG = ZdGraph.decode(gc, 0, gc.length);

// 多段文档 / multi-segment document
byte[] doc2 = ZdSegments.assemble(0, List.of(
        new ZdSegments.Slice(ZdFooter.SEG_DATA, "", ZdDocWriter.writeBody(rows)),
        new ZdSegments.Slice(ZdFooter.SEG_SCHEMA, "", schema.encodeSegment()),
        new ZdSegments.Slice(ZdFooter.SEG_CUSTOM, "meta", new byte[]{1, 2, 3}));
List<ZdSegments.Slice> slices = ZdSegments.read(doc2);   // 经索引定位 / via the index
```

## 构建 / Build

* 仓内 Gradle wrapper：`./gradlew probe`（确定性探针 / the deterministic probe）
* Maven：`mvn package`（pom 为规范构建描述 / the pom is the canonical build description）
* 纯 javac（离线 / offline）：

```bash
javac -encoding UTF-8 -d out $(find src/main/java -name "*.java")
java -cp out org.tielang.zd.ZdProbe   # 121 checks，exit 0 = PASS / exit 0 = PASS
```

* tsha1f KAT 向量（权威源 tiec `tests/tsha_probe/gen_tsha1_core.py`）存于
  `scripts/tsha1f-kat.txt` / the tsha1f KAT vectors live at `scripts/tsha1f-kat.txt`
* 发布（Maven Central，Sonatype Central Portal）见 [docs/publishing.md](docs/publishing.md)
  / publishing is covered by [docs/publishing.md](docs/publishing.md)

## License

本项目以 Tie Public License 2.2（TPL 2.2）发布——见 [LICENSE](LICENSE)。

This project is licensed under the Tie Public License 2.2 (TPL 2.2) — see
[LICENSE](LICENSE).
