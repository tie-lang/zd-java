# zd-java ROAD

zd（tie 生态二进制序列化格式）Java 编解码库的落地线。双轨编号：`p.<发布档>.<模块>.<子项>`
预发布、`r.<发布档>.<模块>.<子项>` 正式（仅优化稳定）。

The landing line of the zd (tie-ecosystem binary serialization) Java codec library.
Two-track versioning: `p.<release>.<module>.<sub-item>` pre-release, `r.<release>.<module>.<sub-item>`
release (stabilization only).

## 里程碑 / Milestones

| 轨 / Track | 内容 / Content | 状态 / Status |
| --- | --- | --- |
| p.1.1 | zd v3 载体全量：头 + flags + 索引 footer + 行/树层 + 多段文档 / Full zd v3 carrier: header + flags + index footer + row/tree layers + multi-segment documents | landed / 已落地 |
| p.1.2 | 列式编码族（plain/RLE/delta/字典）+ 字符串池 + 字典引用 / The columnar encoding family + the string pool + dictionary references | landed / 已落地 |
| p.1.3 | schema 演进（三态 + 字段号不复用 + schema_id）+ tsha1f Java 移植（KAT 对齐 gen_tsha1_core.py）/ Schema evolution + the tsha1f Java port (KAT-aligned) | landed / 已落地 |
| p.1.4 | 标准类型三件（timestamp/decimal/uuid）+ 图容器（段 + ext）+ 压缩载荷段 / The three standard types + the graph container + compressed segments | landed / 已落地 |
| p.1.5 | 生态对齐：KAT 向量集发 tie-spec 仓 + 跨语言一致性复核 / Ecosystem alignment: the KAT vector set to the tie-spec repo + cross-language consistency review | planned / 计划 |

## 设计纪律 / Design Discipline

* 零依赖、纯 JDK 17+；确定性纯函数——同输入同字节，非法输入确定性拒绝（IAE），不静默降级
* 字节级权威 = 语言规范 §17.2 + `2026-09-28-zd-v3-design.md` + tiedb `src/zd*.tie`
  （字典引用 `0xC8`、列式容器 `0xD6`、ext `0xD7 + tag varint + len varint` 均按权威形态）
* 本库钉定而设计案未明的三处（随 KAT 向量集同步 tie-spec 仓）：footer 段名称 =
  varint 长度前缀 UTF-8；v3 列头 = `[类型][编码][列长]`；schema 段 = fixmap(id/fields)
  + 图容器 zd 值嵌入 = varint 行数 + 行数 × 六字段记录
* Zero dependencies, pure JDK 17+; deterministic pure functions — same input → same
  bytes, illegal input deterministically rejected (IAE), never silently degraded
* The byte-level authority = language-spec §17.2 + the zd v3 design + tiedb
  `src/zd*.tie` (dictionary ref `0xC8`, columnar `0xD6`, ext `0xD7 + tag varint +
  len varint` all per the authoritative forms)
* Three spots pinned by this library where the design is silent (to be synced to the
  tie-spec KAT set): footer segment names = varint-length-prefixed UTF-8; the v3 column
  header = `[type][encoding][length]`; the schema segment = fixmap(id/fields); the graph
  value embedding = varint rowCount + rowCount × six-field records
