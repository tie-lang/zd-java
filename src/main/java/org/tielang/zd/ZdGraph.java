package org.tielang.zd;

import java.io.ByteArrayOutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * zd v3 图容器（语言无关的图序列化，{@code 2026-09-28-zd-v3-design.md} §6）：新段类型
 * （段类型 4），同时可作为 ext 子类型（类型标记 {@code 0x47 'G'}）嵌入任意 zd 值位置。
 * <pre>
 * 图容器 = [声明 1B][节点数 varint][节点表][边数 varint][边表]
 * 声明字节：bit0 有向（0 无向 / 1 有向）；bit1 节点 id 形态（0 varint 索引 / 1 string 显式 id）
 * 节点 = [id][载荷：任意 zd 值][属性：map（可空）]
 * 边   = [from: 节点引用 varint][to: 节点引用 varint][边头 1B][标签 string（可选）]
 *        [权重 f64 BE（可选）][属性 map（可选）]
 * 边头字节：bit0 有向覆盖（与容器声明异或生效）；bit1 带标签；bit2 带权重；bit3 带属性
 * </pre>
 * 约束：节点 id 唯一；边的 from/to 必须指向存在的节点（编解码双侧校验）；自环与多重边
 * 合法；空图（0 节点 0 边）合法。<b>zd 值嵌入形态（zd-java 钉定）</b>：任意 zd 值 /
 * 属性 map 在值位置嵌入为 {@code varint 行数 + 行数 × 六字段 wire2 记录}（自定界子树，
 * 见 {@link ZdTrees}）。tie 侧图的边与节点操作产生/消费此容器；节点载荷可携带 code 等
 * ext 载荷；大图的节点表与边表应当用列式容器 + 编码族承载（本类提供整图容器，列式承载
 * 由调用方组合 {@link ZdColumnar}）。
 * <p>
 * The zd v3 graph container (language-agnostic graph serialisation, §6 of the v3
 * design): a new segment type (type 4) that can also ride as an ext subtype (marker
 * {@code 0x47 'G'}) at any zd value position. Structure and edge-head bits as pinned
 * above. Constraints: node ids unique; edge endpoints must reference existing nodes
 * (validated on both encode and decode); self-loops and multi-edges legal; the empty
 * graph legal. <b>zd value embedding (pinned by zd-java):</b> any zd value / attribute
 * map embeds at a value position as {@code varint rowCount + rowCount × six-field
 * wire2 records} (a self-delimiting subtree, see {@link ZdTrees}). Tie-side graph edge
 * and node operations produce/consume this container; node payloads may carry code and
 * other ext payloads; large graphs should carry their node/edge tables through the
 * columnar container + encoding family (callers compose {@link ZdColumnar}).
 */
public final class ZdGraph {

    /** 图容器段字节无前缀标记（段类型 4 载荷即容器字节本身）。 /
     *  The graph container has no prefix (the type-4 segment payload is the container itself). */
    public static final int DECL_DIRECTED = 1;
    /** 声明位 1：节点 id 用 string 显式 id。 / Declaration bit 1: node ids are explicit strings. */
    public static final int DECL_STRING_IDS = 2;

    /** 边头位 0：有向覆盖（与容器声明异或生效）。 / Edge-head bit 0: directed override (XOR with the container declaration). */
    public static final int EDGE_DIRECTED_OVERRIDE = 1;
    /** 边头位 1：带标签。 / Edge-head bit 1: has a label. */
    public static final int EDGE_HAS_LABEL = 2;
    /** 边头位 2：带权重。 / Edge-head bit 2: has a weight. */
    public static final int EDGE_HAS_WEIGHT = 4;
    /** 边头位 3：带属性。 / Edge-head bit 3: has attributes. */
    public static final int EDGE_HAS_ATTRS = 8;

    private ZdGraph() {
    }

    /** 图节点：id（varint 索引形态为 Long、string 形态为 String）+ 任意 zd 载荷 + 可空属性 map。 /
     *  A graph node: id (Long in the varint form, String in the string form) + any zd
     *  payload + optional attribute map. */
    public record GNode(Object id, ZdNode payload, ZdNode attrs) {
    }

    /** 图边：两端节点引用 + 可选有向覆盖 / 标签 / 权重 / 属性（null = 缺省）。 /
     *  A graph edge: endpoint references + optional directed override / label / weight /
     *  attributes (null = absent). */
    public record GEdge(int from, int to, Boolean directedOverride, String label,
                        Double weight, ZdNode attrs) {
    }

    /** 图数据：方向 + id 形态 + 节点表 + 边表。 / The graph data: direction + id form + node table + edge table. */
    public record GraphData(boolean directed, boolean stringIds,
                            List<GNode> nodes, List<GEdge> edges) {
    }

    // ==================== 编码（写侧） / encoding (write side) ====================

    /**
     * 编码图容器字节。约束违约（id 混型 / 重复 / 边引用越界）确定性 IAE。
     * Encodes the graph container bytes. Constraint violations (mixed-type / duplicate
     * ids, out-of-range edge references) are deterministic IAEs.
     */
    public static byte[] encode(GraphData g) {
        validate(g);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int decl = (g.directed() ? DECL_DIRECTED : 0) | (g.stringIds() ? DECL_STRING_IDS : 0);
        out.write(decl);
        ZdPrimitives.writeVarint(out, g.nodes().size());
        for (GNode n : g.nodes()) {
            if (g.stringIds()) {
                writeAll(out, ZdPrimitives.encodeString((String) n.id()));
            } else {
                ZdPrimitives.writeVarint(out, (Long) n.id());
            }
            writeValue(out, n.payload());
            if (n.attrs() == null) {
                out.write(0x00);
            } else {
                out.write(0x01);
                writeValue(out, n.attrs());
            }
        }
        ZdPrimitives.writeVarint(out, g.edges().size());
        for (GEdge e : g.edges()) {
            ZdPrimitives.writeVarint(out, e.from());
            ZdPrimitives.writeVarint(out, e.to());
            int head = 0;
            if (e.directedOverride() != null) {
                head |= EDGE_DIRECTED_OVERRIDE;
            }
            if (e.label() != null) {
                head |= EDGE_HAS_LABEL;
            }
            if (e.weight() != null) {
                head |= EDGE_HAS_WEIGHT;
            }
            if (e.attrs() != null) {
                head |= EDGE_HAS_ATTRS;
            }
            out.write(head);
            if (e.directedOverride() != null) {
                out.write(e.directedOverride() ? 1 : 0);
            }
            if (e.label() != null) {
                writeAll(out, ZdPrimitives.encodeString(e.label()));
            }
            if (e.weight() != null) {
                writeAll(out, ZdPrimitives.be64(Double.doubleToLongBits(e.weight())));
            }
            if (e.attrs() != null) {
                writeValue(out, e.attrs());
            }
        }
        return out.toByteArray();
    }

    /**
     * 以 ext 子类型（标记 0x47）编码图容器——任意 zd 值位置可嵌。
     * Encodes the graph container as the ext subtype (marker 0x47) — embeddable at any
     * zd value position.
     */
    public static byte[] encodeExt(GraphData g) {
        return ZdExt.encode(ZdExt.TAG_GRAPH, encode(g));
    }

    // ==================== 解码（读侧） / decoding (read side) ====================

    /**
     * 解码图容器字节。畸形 / 约束违约确定性 IAE（含 id 重复与边引用越界）。
     * Decodes the graph container bytes. Malformed / constraint-violating inputs
     * (incl. duplicate ids and out-of-range edge references) are deterministic IAEs.
     */
    public static GraphData decode(byte[] b, int off, int len) {
        if (off < 0 || len < 0 || off > b.length || len > b.length - off) {
            throw new IllegalArgumentException("zd graph container range out of bounds");
        }
        int end = off + len;
        int[] pos = {off};
        int decl = b[pos[0]++] & 0xFF;
        boolean directed = (decl & DECL_DIRECTED) != 0;
        boolean stringIds = (decl & DECL_STRING_IDS) != 0;
        long nodeCount = ZdPrimitives.readVarint(b, pos);
        if (nodeCount < 0 || nodeCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("zd graph node count out of range: " + nodeCount);
        }
        java.util.List<GNode> nodes = new java.util.ArrayList<>((int) nodeCount);
        Set<Object> ids = new HashSet<>();
        for (long i = 0; i < nodeCount; i++) {
            Object id = stringIds ? ZdPrimitives.decodeString(b, pos)
                    : (Object) ZdPrimitives.readVarint(b, pos);
            if (!ids.add(id)) {
                throw new IllegalArgumentException("zd graph node id is not unique: " + id);
            }
            ZdNode payload = readValue(b, pos, end);
            ZdNode attrs = null;
            int flag = b[pos[0]++] & 0xFF;
            if (flag == 0x01) {
                attrs = readValue(b, pos, end);
            } else if (flag != 0x00) {
                throw new IllegalArgumentException("zd graph node attrs flag must be 0/1: " + flag);
            }
            nodes.add(new GNode(id, payload, attrs));
        }
        long edgeCount = ZdPrimitives.readVarint(b, pos);
        if (edgeCount < 0 || edgeCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("zd graph edge count out of range: " + edgeCount);
        }
        java.util.List<GEdge> edges = new java.util.ArrayList<>((int) edgeCount);
        for (long i = 0; i < edgeCount; i++) {
            long from = ZdPrimitives.readVarint(b, pos);
            long to = ZdPrimitives.readVarint(b, pos);
            if (from >= nodeCount || to >= nodeCount) {
                throw new IllegalArgumentException("zd graph edge references a missing node (from " + from + ", to " + to + ", nodes " + nodeCount + ")");
            }
            int head = b[pos[0]++] & 0xFF;
            Boolean override = (head & EDGE_DIRECTED_OVERRIDE) != 0
                    ? (b[pos[0]++] & 0xFF) == 1 : null;
            String label = (head & EDGE_HAS_LABEL) != 0 ? ZdPrimitives.decodeString(b, pos) : null;
            Double weight = null;
            if ((head & EDGE_HAS_WEIGHT) != 0) {
                if (pos[0] + 8 > end) {
                    throw new IllegalArgumentException("zd graph edge weight overruns the container");
                }
                weight = Double.longBitsToDouble(ZdPrimitives.readBe64(b, pos[0]));
                pos[0] += 8;
            }
            ZdNode attrs = (head & EDGE_HAS_ATTRS) != 0 ? readValue(b, pos, end) : null;
            edges.add(new GEdge((int) from, (int) to, override, label, weight, attrs));
        }
        if (pos[0] != end) {
            throw new IllegalArgumentException("zd graph container has trailing bytes ("
                    + (end - pos[0]) + " unparsed)");
        }
        return new GraphData(directed, stringIds, nodes, edges);
    }

    /** 解码 ext 子类型（标记 0x47）为图数据。Decodes the ext subtype (marker 0x47) as graph data. */
    public static GraphData decodeExt(byte[] ext) {
        int[] pos = {0};
        ZdExt.Decoded d = ZdExt.decode(ext, pos);
        if (d.tag() != ZdExt.TAG_GRAPH) {
            throw new IllegalArgumentException("expected graph ext tag 0x47 but got " + d.tag());
        }
        if (pos[0] != ext.length) {
            throw new IllegalArgumentException("graph ext value has trailing bytes");
        }
        return decode(d.payload(), 0, d.payload().length);
    }

    // ==================== 约束校验 / constraint validation ====================

    private static void validate(GraphData g) {
        Set<Object> ids = new HashSet<>();
        for (GNode n : g.nodes()) {
            if (g.stringIds()) {
                if (!(n.id() instanceof String s) || s.isEmpty()) {
                    throw new IllegalArgumentException("zd graph string-id node requires a non-empty String id");
                }
            } else {
                if (!(n.id() instanceof Long l) || l < 0) {
                    throw new IllegalArgumentException("zd graph varint-id node requires a non-negative Long id");
                }
            }
            if (!ids.add(n.id())) {
                throw new IllegalArgumentException("zd graph node id is not unique: " + n.id());
            }
        }
        int n = g.nodes().size();
        for (GEdge e : g.edges()) {
            if (e.from() < 0 || e.from() >= n || e.to() < 0 || e.to() >= n) {
                throw new IllegalArgumentException("zd graph edge references a missing node (from "
                        + e.from() + ", to " + e.to() + ", nodes " + n + ")");
            }
        }
    }

    // ==================== zd 值嵌入 / zd value embedding ====================

    /**
     * zd 值嵌入：{@code varint 行数 + 行数 × 六字段 wire2 记录}（自定界子树）。
     * The zd value embedding: {@code varint rowCount + rowCount × six-field wire2
     * records} (a self-delimiting subtree).
     */
    static void writeValue(ByteArrayOutputStream out, ZdNode value) {
        java.util.List<ZdRow> rows = ZdTrees.flatten(value);
        ZdPrimitives.writeVarint(out, rows.size());
        for (ZdRow r : rows) {
            ZdDocWriter.writeRow(out, r);
        }
    }

    /** 逆嵌入：读自定界子树回 {@link ZdNode}。 / The reverse embedding: reads the self-delimiting subtree back. */
    static ZdNode readValue(byte[] b, int[] pos, int end) {
        long count = ZdPrimitives.readVarint(b, pos);
        if (count < 1 || count > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("zd embedded value row count out of range: " + count);
        }
        int save = pos[0];
        int p = save;
        for (long i = 0; i < count; i++) {
            p = skipRecord(b, p, end);
        }
        List<ZdRow> rows = ZdVolume.readRowsRange(b, save, p - save);
        pos[0] = p;
        if (rows.size() != count) {
            throw new IllegalArgumentException("zd embedded value row count mismatch: header " + count + ", parsed " + rows.size());
        }
        return ZdTrees.buildNode(rows);
    }

    /**
     * 跳过一条六字段 wire2 记录：{@link ZdDocWriter#writeRow} 恒写 tag 10/18/26/34/42/50
     * 六字段、顺序固定——逐字段精确消费并校验 tag（嵌入子树由本库写出，形态自洽）。
     * Skips one six-field wire2 record: {@link ZdDocWriter#writeRow} always writes the
     * six fields with tags 10/18/26/34/42/50 in a fixed order — consume exactly six
     * fields with tag verification (embedded subtrees are written by this library, so
     * the form is self-consistent).
     */
    private static int skipRecord(byte[] b, int p, int end) {
        int[] tags = {10, 18, 26, 34, 42, 50};
        for (int tag : tags) {
            int[] q = {p};
            long t = ZdPrimitives.readVarint(b, q);
            long flen = ZdPrimitives.readVarint(b, q);
            if (t != tag) {
                throw new IllegalArgumentException("zd embedded record field tag mismatch (expected " + tag + ", got " + t + ")");
            }
            if (q[0] + flen > end) {
                throw new IllegalArgumentException("zd embedded record overruns the container");
            }
            p = q[0] + (int) flen;
        }
        return p;
    }

    private static void writeAll(ByteArrayOutputStream out, byte[] b) {
        out.write(b, 0, b.length);
    }
}
