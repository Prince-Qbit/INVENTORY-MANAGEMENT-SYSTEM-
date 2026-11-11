import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * StockPilot backend - zero dependencies, needs only JDK 11+.
 * Run:  java StockPilotServer.java [port] [webRoot]
 * Serves index.html/app.js/style.css/images from webRoot (default ".") and a REST API under /api.
 * Data is persisted to data.json next to the server.
 */
public class StockPilotServer {
    static final Object LOCK = new Object();
    static final Path DATA_FILE = Paths.get("data.json");
    static final Map<String, Map<String, Object>> SESSIONS = new ConcurrentHashMap<>();
    static Path webRoot = Paths.get(".");

    // ---- data ----
    static List<Map<String, Object>> products = new ArrayList<>();
    static List<Map<String, Object>> sales = new ArrayList<>();
    static List<Map<String, Object>> updates = new ArrayList<>();
    static List<Map<String, Object>> orders = new ArrayList<>();

    static class ApiException extends RuntimeException {
        final int status;
        ApiException(int status, String msg) { super(msg); this.status = status; }
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        if (args.length > 1) webRoot = Paths.get(args[1]);
        load();
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", StockPilotServer::handle);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("StockPilot running at http://localhost:" + port);
        System.out.println("Logins: admin/admin123  cashier/cashier123");
    }

    // ================= HTTP =================
    @SuppressWarnings("unchecked")
    static void handle(HttpExchange ex) throws IOException {
        try {
            ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");
            ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
            if (ex.getRequestMethod().equals("OPTIONS")) { ex.sendResponseHeaders(204, -1); return; }
            String path = ex.getRequestURI().getPath();
            if (path.startsWith("/api/")) {
                Object result;
                try {
                    Object body = null;
                    byte[] raw = ex.getRequestBody().readAllBytes();
                    if (raw.length > 0) body = Json.parse(new String(raw, StandardCharsets.UTF_8));
                    synchronized (LOCK) { result = route(ex.getRequestMethod(), path, body, ex); }
                } catch (ApiException e) {
                    sendJson(ex, e.status, Map.of("error", e.getMessage())); return;
                } catch (IllegalArgumentException e) {
                    sendJson(ex, 400, Map.of("error", "Invalid JSON: " + e.getMessage())); return;
                }
                sendJson(ex, 200, result);
            } else serveStatic(ex, path);
        } catch (Exception e) {
            e.printStackTrace();
            try { sendJson(ex, 500, Map.of("error", "Server error")); } catch (Exception ignored) {}
        } finally { ex.close(); }
    }

    static void sendJson(HttpExchange ex, int status, Object obj) throws IOException {
        byte[] out = Json.stringify(obj).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, out.length);
        ex.getResponseBody().write(out);
    }

    static void serveStatic(HttpExchange ex, String path) throws IOException {
        if (path.equals("/")) path = "/index.html";
        Path root = webRoot.toAbsolutePath().normalize();
        Path file = root.resolve(path.substring(1)).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            byte[] nf = "Not found".getBytes();
            ex.sendResponseHeaders(404, nf.length); ex.getResponseBody().write(nf); return;
        }
        String n = file.getFileName().toString().toLowerCase();
        String type = n.endsWith(".html") ? "text/html; charset=utf-8" : n.endsWith(".js") ? "application/javascript; charset=utf-8"
                : n.endsWith(".css") ? "text/css; charset=utf-8" : n.endsWith(".svg") ? "image/svg+xml"
                : n.endsWith(".png") ? "image/png" : n.endsWith(".jpg") || n.endsWith(".jpeg") ? "image/jpeg"
                : n.endsWith(".webp") ? "image/webp" : n.endsWith(".json") ? "application/json" : "application/octet-stream";
        byte[] data = Files.readAllBytes(file);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(200, data.length);
        ex.getResponseBody().write(data);
    }

    // ================= ROUTER =================
    @SuppressWarnings("unchecked")
    static Object route(String method, String path, Object bodyObj, HttpExchange ex) {
        Map<String, Object> body = bodyObj instanceof Map ? (Map<String, Object>) bodyObj : new LinkedHashMap<>();
        String[] p = path.substring(5).split("/"); // after "/api/"

        if (p[0].equals("login") && method.equals("POST")) return login(body);

        Map<String, Object> user = auth(ex);
        boolean admin = "admin".equals(user.get("role"));

        switch (p[0]) {
            case "me": return user;
            case "logout": SESSIONS.remove(token(ex)); return ok();
            case "dashboard": return dashboard(admin);
            case "categories": return categories();
            case "products":
                if (p.length == 1) {
                    if (method.equals("GET")) return products;
                    if (method.equals("POST")) { requireAdmin(admin); return saveProduct(null, body, user); }
                } else {
                    long id = parseId(p[1]);
                    if (p.length == 2 && method.equals("GET")) return findProduct(id);
                    if (p.length == 2 && method.equals("PUT")) { requireAdmin(admin); return saveProduct(id, body, user); }
                    if (p.length == 2 && method.equals("DELETE")) { requireAdmin(admin); return deleteProduct(id, user); }
                    if (p.length == 3 && p[2].equals("restock") && method.equals("POST")) { requireAdmin(admin); return restock(id, body, user); }
                }
                break;
            case "sales":
                if (method.equals("POST")) return checkout(body, user);
                if (method.equals("GET")) { requireAdmin(admin); return sales; }
                break;
            case "updates":
                if (method.equals("GET")) { requireAdmin(admin); return updates; }
                break;
            case "orders":
                if (p.length == 1 && method.equals("GET")) return orders;
                if (p.length == 1 && method.equals("POST")) return saveOrder(body);
                if (p.length == 2 && method.equals("DELETE")) {
                    if (!orders.removeIf(o -> o.get("id").equals(p[1]))) throw new ApiException(404, "Order not found");
                    save(); return ok();
                }
                if (p.length == 3 && p[2].equals("open") && method.equals("POST")) return openOrder(p[1]);
                break;
        }
        throw new ApiException(404, "Unknown endpoint: " + method + " " + path);
    }

    // ================= AUTH =================
    static Object login(Map<String, Object> b) {
        String u = str(b.get("username")).trim().toLowerCase(), pw = str(b.get("password"));
        String name, role;
        if (u.equals("admin") && pw.equals("admin123")) { name = "Admin"; role = "admin"; }
        else if (u.equals("cashier") && pw.equals("cashier123")) { name = "Cashier"; role = "cashier"; }
        else throw new ApiException(401, "Incorrect username or password.");
        String token = UUID.randomUUID().toString();
        Map<String, Object> s = obj("name", name, "role", role);
        SESSIONS.put(token, s);
        return obj("token", token, "name", name, "role", role);
    }

    static String token(HttpExchange ex) {
        String h = ex.getRequestHeaders().getFirst("Authorization");
        return h != null && h.startsWith("Bearer ") ? h.substring(7) : "";
    }

    static Map<String, Object> auth(HttpExchange ex) {
        Map<String, Object> s = SESSIONS.get(token(ex));
        if (s == null) throw new ApiException(401, "Please sign in.");
        return s;
    }

    static void requireAdmin(boolean admin) { if (!admin) throw new ApiException(403, "Administrator access required."); }

    // ================= PRODUCTS =================
    static Map<String, Object> findProduct(long id) {
        for (Map<String, Object> p : products) if (num(p.get("id")) == id) return p;
        throw new ApiException(404, "Product not found");
    }

    static Object saveProduct(Long id, Map<String, Object> b, Map<String, Object> user) {
        String name = str(b.get("name")).trim(), sku = str(b.get("sku")).trim();
        if (name.isEmpty() || sku.isEmpty()) throw new ApiException(400, "Product name and SKU are required.");
        double cost = num(b.get("cost")), selling = num(b.get("selling")), qty = num(b.get("quantity")), th = b.get("threshold") == null ? 5 : num(b.get("threshold"));
        if (cost < 0 || selling < 0 || qty < 0 || th < 0 || qty != Math.floor(qty)) throw new ApiException(400, "Enter valid numbers.");
        for (Map<String, Object> o : products)
            if (str(o.get("sku")).equalsIgnoreCase(sku) && (id == null || num(o.get("id")) != id))
                throw new ApiException(409, "That SKU is already being used.");
        String category = str(b.get("category")).trim();
        if (category.isEmpty()) category = "Uncategorised";
        String image = str(b.get("image"));

        if (id == null) {
            Map<String, Object> p = obj("id", System.currentTimeMillis(), "name", name, "sku", sku, "category", category,
                    "cost", cost, "selling", selling, "quantity", (long) qty, "threshold", (long) th, "image", image);
            products.add(p);
            if (qty > 0) recordUpdate(p, 0, (long) qty, "Opening stock", "Stock update", user);
            save(); return p;
        }
        Map<String, Object> p = findProduct(id);
        long prev = (long) num(p.get("quantity"));
        p.put("name", name); p.put("sku", sku); p.put("category", category); p.put("cost", cost);
        p.put("selling", selling); p.put("quantity", (long) qty); p.put("threshold", (long) th);
        if (!image.isEmpty()) p.put("image", image);
        long delta = (long) qty - prev;
        if (delta != 0) recordUpdate(p, prev, delta, "Manual stock adjustment", "Stock update", user);
        save(); return p;
    }

    static Object deleteProduct(long id, Map<String, Object> user) {
        Map<String, Object> p = findProduct(id);
        long prev = (long) num(p.get("quantity"));
        Map<String, Object> u = recordUpdate(p, prev, -prev, "Product deleted from the product list", "Product deleted", user);
        u.put("newBalance", 0L); u.put("deleted", true);
        u.put("deletedCost", p.get("cost")); u.put("deletedSelling", p.get("selling"));
        products.remove(p);
        save(); return ok();
    }

    static Object restock(long id, Map<String, Object> b, Map<String, Object> user) {
        Map<String, Object> p = findProduct(id);
        double q = num(b.get("quantity"));
        if (q <= 0 || q != Math.floor(q)) throw new ApiException(400, "Enter a whole number greater than zero.");
        long prev = (long) num(p.get("quantity"));
        p.put("quantity", prev + (long) q);
        String note = str(b.get("note")).trim();
        recordUpdate(p, prev, (long) q, note.isEmpty() ? "Stock added" : note, "Stock update", user);
        save(); return p;
    }

    static Map<String, Object> recordUpdate(Map<String, Object> p, long prev, long added, String note, String action, Map<String, Object> user) {
        Map<String, Object> u = obj("id", System.nanoTime(), "productId", p.get("id"), "product", p.get("name"), "sku", p.get("sku"),
                "date", Instant.now().toString(), "previousStock", prev, "quantityAdded", added,
                "newBalance", (long) num(p.get("quantity")), "admin", user.get("name"), "note", note, "action", action);
        updates.add(0, u);
        return u;
    }

    static Object categories() {
        TreeSet<String> set = new TreeSet<>();
        for (Map<String, Object> p : products) if (!str(p.get("category")).isEmpty()) set.add(str(p.get("category")));
        return new ArrayList<>(set);
    }

    // ================= SALES =================
    @SuppressWarnings("unchecked")
    static Object checkout(Map<String, Object> b, Map<String, Object> user) {
        List<Object> items = b.get("items") instanceof List ? (List<Object>) b.get("items") : List.of();
        if (items.isEmpty()) throw new ApiException(400, "Add products to the order first.");
        String method = str(b.get("payment"));
        if (!List.of("Cash", "Mobile Money", "Card").contains(method)) throw new ApiException(400, "Invalid payment method.");

        Map<Long, Long> wanted = new LinkedHashMap<>();
        for (Object o : items) {
            Map<String, Object> m = (Map<String, Object>) o;
            double rawQuantity = num(m.get("quantity"));
            if (rawQuantity != Math.floor(rawQuantity)) throw new ApiException(400, "Invalid quantity.");
            long q = (long) rawQuantity;
            if (q <= 0) throw new ApiException(400, "Invalid quantity.");
            wanted.merge((long) num(m.get("id")), q, Long::sum);
        }
        double total = 0;
        List<Object> lines = new ArrayList<>();
        for (Map.Entry<Long, Long> e : wanted.entrySet()) {
            Map<String, Object> p = findProduct(e.getKey());
            if ((long) num(p.get("quantity")) < e.getValue()) throw new ApiException(409, "Not enough stock for " + p.get("name") + ".");
            total += num(p.get("selling")) * e.getValue();
            lines.add(obj("id", p.get("id"), "name", p.get("name"), "quantity", e.getValue(), "price", p.get("selling")));
        }
        total = Math.round(total * 100) / 100.0;
        double received = method.equals("Cash") ? num(b.get("received")) : total;
        if (method.equals("Cash") && received < total) throw new ApiException(400, "Amount received is less than the total.");

        for (Map.Entry<Long, Long> e : wanted.entrySet()) {
            Map<String, Object> p = findProduct(e.getKey());
            p.put("quantity", (long) num(p.get("quantity")) - e.getValue());
        }
        Map<String, Object> sale = obj("id", "SP-" + System.currentTimeMillis(), "date", Instant.now().toString(),
                "cashier", user.get("name"), "payment", method, "total", total, "received", received,
                "change", Math.max(0, Math.round((received - total) * 100) / 100.0), "items", lines);
        sales.add(0, sale);
        save(); return sale;
    }

    // ================= ORDERS =================
    @SuppressWarnings("unchecked")
    static Object saveOrder(Map<String, Object> b) {
        List<Object> items = b.get("items") instanceof List ? (List<Object>) b.get("items") : List.of();
        if (items.isEmpty()) throw new ApiException(400, "There is no order to save.");
        double total = 0;
        List<Object> lines = new ArrayList<>();
        for (Object o : items) {
            Map<String, Object> m = (Map<String, Object>) o;
            Map<String, Object> p = findProduct((long) num(m.get("id")));
            double rawQuantity = num(m.get("quantity"));
            if (rawQuantity != Math.floor(rawQuantity)) throw new ApiException(400, "Invalid quantity.");
            long q = (long) rawQuantity;
            if (q <= 0) throw new ApiException(400, "Invalid quantity.");
            total += num(p.get("selling")) * q;
            lines.add(obj("id", p.get("id"), "name", p.get("name"), "selling", p.get("selling"), "quantity", q));
        }
        Map<String, Object> order = obj("id", "ORD-" + System.currentTimeMillis(), "date", Instant.now().toString(),
                "items", lines, "total", Math.round(total * 100) / 100.0);
        orders.add(0, order);
        save(); return order;
    }

    @SuppressWarnings("unchecked")
    static Object openOrder(String id) {
        Map<String, Object> o = null;
        for (Map<String, Object> x : orders) if (x.get("id").equals(id)) o = x;
        if (o == null) throw new ApiException(404, "Order not found");
        for (Object it : (List<Object>) o.get("items")) {
            Map<String, Object> m = (Map<String, Object>) it;
            Map<String, Object> p;
            try { p = findProduct((long) num(m.get("id"))); } catch (ApiException e) { throw new ApiException(409, "Not enough stock for " + m.get("name") + "."); }
            if ((long) num(p.get("quantity")) < (long) num(m.get("quantity"))) throw new ApiException(409, "Not enough stock for " + m.get("name") + ".");
        }
        orders.remove(o);
        save(); return o;
    }

    // ================= DASHBOARD =================
    @SuppressWarnings("unchecked")
    static Object dashboard(boolean admin) {
        LocalDate today = LocalDate.now();
        double total = 0, cash = 0, mobile = 0, card = 0; long items = 0, count = 0;
        for (Map<String, Object> s : sales) {
            if (!Instant.parse(str(s.get("date"))).atZone(ZoneId.systemDefault()).toLocalDate().equals(today)) continue;
            double t = num(s.get("total")); total += t; count++;
            switch (str(s.get("payment"))) { case "Cash": cash += t; break; case "Mobile Money": mobile += t; break; default: card += t; }
            for (Object i : (List<Object>) s.get("items")) items += (long) num(((Map<String, Object>) i).get("quantity"));
        }
        double inv = 0; List<Object> alerts = new ArrayList<>();
        for (Map<String, Object> p : products) {
            inv += num(p.get("cost")) * num(p.get("quantity"));
            if (num(p.get("quantity")) <= num(p.get("threshold"))) alerts.add(p);
        }
        long updatesToday = 0, unitsToday = 0;
        for (Map<String, Object> u : updates) {
            if (!Instant.parse(str(u.get("date"))).atZone(ZoneId.systemDefault()).toLocalDate().equals(today)) continue;
            updatesToday++; if (num(u.get("quantityAdded")) > 0) unitsToday += (long) num(u.get("quantityAdded"));
        }
        Map<String, Object> d = obj("todaySales", total, "todayTransactions", count, "itemsSoldToday", items,
                "totalProducts", (long) products.size(), "lowStockCount", (long) alerts.size(), "alerts", alerts,
                "recentSales", sales.subList(0, Math.min(5, sales.size())));
        if (admin) {
            d.put("inventoryValue", inv); d.put("cashToday", cash); d.put("mobileToday", mobile); d.put("cardToday", card);
            d.put("updatesToday", updatesToday); d.put("unitsAddedToday", unitsToday);
        }
        return d;
    }

    // ================= PERSISTENCE =================
    @SuppressWarnings("unchecked")
    static void load() {
        try {
            if (Files.exists(DATA_FILE)) {
                Map<String, Object> d = (Map<String, Object>) Json.parse(Files.readString(DATA_FILE));
                products = castList(d.get("products")); sales = castList(d.get("sales"));
                updates = castList(d.get("updates")); orders = castList(d.get("orders"));
                return;
            }
        } catch (Exception e) { System.err.println("Could not read data.json, starting fresh: " + e); }
        Object[][] seed = {{"Bottled Water","SKU001","Drinks",2,3,4},{"Orange Juice","SKU002","Drinks",7,10,20},
                {"Potato Chips","SKU003","Snacks",4,6,3},{"Chocolate Bar","SKU004","Snacks",5,8,15},
                {"Milk Carton","SKU005","Dairy",8,12,0},{"Bread","SKU006","Bakery",5,7,12}};
        long id = 1;
        for (Object[] s : seed) {
            products.add(obj("id", id++, "name", s[0], "sku", s[1], "category", s[2], "cost", ((Number) s[3]).doubleValue(),
                    "selling", ((Number) s[4]).doubleValue(), "quantity", ((Number) s[5]).longValue(), "threshold", 5L, "image", ""));
        }
        save();
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> castList(Object o) { return o instanceof List ? new ArrayList<>((List<Map<String, Object>>) o) : new ArrayList<>(); }

    static void save() {
        try {
            Map<String, Object> d = obj("products", products, "sales", sales, "updates", updates, "orders", orders);
            Path tmp = Paths.get("data.json.tmp");
            Files.writeString(tmp, Json.stringify(d));
            Files.move(tmp, DATA_FILE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) { System.err.println("Could not save data: " + e); }
    }

    // ================= HELPERS =================
    static Map<String, Object> obj(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
    static Map<String, Object> ok() { return obj("ok", true); }
    static String str(Object o) { return o == null ? "" : String.valueOf(o); }
    static double num(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { return Double.parseDouble(str(o)); } catch (NumberFormatException e) { return 0; }
    }
    static long parseId(String s) { try { return Long.parseLong(s); } catch (NumberFormatException e) { throw new ApiException(400, "Bad id"); } }

    // ================= MINI JSON =================
    static class Json {
        private final String s; private int i;
        private Json(String s) { this.s = s; }
        static Object parse(String s) {
            Json j = new Json(s); j.ws(); Object v = j.value(); j.ws();
            if (j.i != s.length()) throw new IllegalArgumentException("trailing data at " + j.i);
            return v;
        }
        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        Object value() {
            if (i >= s.length()) throw new IllegalArgumentException("unexpected end");
            char c = s.charAt(i);
            if (c == '{') {
                Map<String, Object> m = new LinkedHashMap<>(); i++; ws();
                if (s.charAt(i) == '}') { i++; return m; }
                while (true) {
                    ws(); String k = string(); ws(); expect(':'); ws(); m.put(k, value()); ws();
                    if (s.charAt(i) == ',') { i++; continue; }
                    expect('}'); return m;
                }
            }
            if (c == '[') {
                List<Object> l = new ArrayList<>(); i++; ws();
                if (s.charAt(i) == ']') { i++; return l; }
                while (true) {
                    ws(); l.add(value()); ws();
                    if (s.charAt(i) == ',') { i++; continue; }
                    expect(']'); return l;
                }
            }
            if (c == '"') return string();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            int st = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (st == i) throw new IllegalArgumentException("unexpected '" + c + "' at " + i);
            return Double.parseDouble(s.substring(st, i));
        }
        void expect(char c) { if (i >= s.length() || s.charAt(i) != c) throw new IllegalArgumentException("expected '" + c + "' at " + i); i++; }
        String string() {
            expect('"'); StringBuilder sb = new StringBuilder();
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n': sb.append('\n'); break; case 't': sb.append('\t'); break; case 'r': sb.append('\r'); break;
                        case 'b': sb.append('\b'); break; case 'f': sb.append('\f'); break;
                        case 'u': sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                        default: sb.append(e);
                    }
                } else sb.append(c);
            }
        }
        static String stringify(Object o) { StringBuilder sb = new StringBuilder(); write(sb, o); return sb.toString(); }
        @SuppressWarnings("unchecked")
        static void write(StringBuilder sb, Object o) {
            if (o == null) sb.append("null");
            else if (o instanceof String) quote(sb, (String) o);
            else if (o instanceof Boolean) sb.append(o);
            else if (o instanceof Double || o instanceof Float) {
                double d = ((Number) o).doubleValue();
                if (d == Math.rint(d) && Math.abs(d) < 1e15) sb.append((long) d); else sb.append(d);
            } else if (o instanceof Number) sb.append(o);
            else if (o instanceof Map) {
                sb.append('{'); boolean first = true;
                for (Map.Entry<String, Object> e : ((Map<String, Object>) o).entrySet()) {
                    if (!first) sb.append(','); first = false;
                    quote(sb, e.getKey()); sb.append(':'); write(sb, e.getValue());
                }
                sb.append('}');
            } else if (o instanceof Iterable) {
                sb.append('['); boolean first = true;
                for (Object x : (Iterable<Object>) o) { if (!first) sb.append(','); first = false; write(sb, x); }
                sb.append(']');
            } else quote(sb, o.toString());
        }
        static void quote(StringBuilder sb, String t) {
            sb.append('"');
            for (char c : t.toCharArray()) {
                switch (c) {
                    case '"': sb.append("\\\""); break; case '\\': sb.append("\\\\"); break;
                    case '\n': sb.append("\\n"); break; case '\r': sb.append("\\r"); break; case '\t': sb.append("\\t"); break;
                    default: if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c);
                }
            }
            sb.append('"');
        }
    }
}
