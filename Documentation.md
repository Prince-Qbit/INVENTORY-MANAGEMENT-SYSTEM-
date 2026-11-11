# StockPilot System Documentation

## 1. Overview

StockPilot is a browser-based inventory and point-of-sale system for a small store. It supports:

- Product and stock management
- Low-stock and out-of-stock alerts
- Point-of-sale checkout
- Saved/current orders
- Sales history
- Product stock-update history
- Dashboard metrics
- Administrator and cashier access

The application uses a zero-dependency Java HTTP server, a static HTML/CSS/JavaScript frontend, and a local JSON data file.

## 2. Technology Stack

| Layer | Technology |
|---|---|
| Frontend | HTML5, CSS3, browser JavaScript |
| Backend | Java 11+ `com.sun.net.httpserver.HttpServer` |
| Storage | `data.json` |
| API | JSON over HTTP |
| Authentication | In-memory bearer-token sessions |
| Icons | Lucide library from CDN |
| Assets | SVG/JPG files in `Frontend/images` |

No database server, Java framework, or build tool is required.

## 3. Repository Structure

```text
INVENTORY-MANAGEMENT-SYSTEM-/
├── Backend/
│   └── StockPilotServer.java
├── Frontend/
│   ├── index.html
│   ├── app.js
│   ├── style.css
│   └── images/
├── Documentation.md
├── Documentation
├── System Design/
├── README.md
└── .gitignore
```

### Main files

- `Backend/StockPilotServer.java`: HTTP server, routing, authentication, business rules, JSON handling, and persistence.
- `Frontend/index.html`: screens, forms, navigation, dialogs, and dashboard layout.
- `Frontend/app.js`: API client, session state, cart behavior, rendering, validation, and event handlers.
- `Frontend/style.css`: visual design and responsive layout.

## 4. Architecture

```text
Browser
  │
  │ GET / and static assets
  │ JSON REST requests under /api
  ▼
StockPilotServer
  ├── Authentication/session map
  ├── Product, sales, update, and order logic
  ├── Role authorization
  ├── JSON serialization/parsing
  └── data.json persistence
```

The Java server serves both the frontend and API. The frontend calls the same-origin `/api` path:

```text
http://localhost:8080/
http://localhost:8080/api/login
http://localhost:8080/api/products
```

The frontend should normally be opened through Java rather than directly from the file system.

## 5. Requirements

- JDK 11 or later
- Modern web browser
- PowerShell, Command Prompt, or another terminal

Verify Java:

```powershell
java -version
javac -version
```

## 6. Installation and Startup

Run these commands from the repository root:

```powershell
javac Backend\StockPilotServer.java
java -cp Backend StockPilotServer 8080 Frontend
```

Open `http://localhost:8080/`.

The server accepts:

```text
java StockPilotServer [port] [webRoot]
```

Example:

```powershell
java -cp Backend StockPilotServer 8091 Frontend
```

Keep the Java process running while using the browser. Stop it with `Ctrl+C`.

If the server is stopped, the frontend reports:

```text
Backend is unavailable. Start StockPilotServer and try again.
```

## 7. Demo Accounts

| Username | Password | Role |
|---|---|---|
| `admin` | `admin123` | Administrator |
| `cashier` | `cashier123` | Cashier |

These are demo credentials defined in the Java source and are not suitable for production.

## 8. Roles and Permissions

| Capability | Administrator | Cashier |
|---|---:|---:|
| Sign in/sign out | Yes | Yes |
| Dashboard | Yes | Yes |
| Products management | Yes | No |
| Add/edit/delete products | Yes | No |
| Restock products | Yes | No |
| Stock alerts | Yes | Yes |
| Add items to cart | Yes | Yes |
| Save/open/delete orders | Yes | Yes |
| Complete sales | Yes | Yes |
| Sales history | Yes | No |
| Product updates | Yes | No |

The backend enforces permissions even if frontend navigation buttons are hidden.

## 9. Frontend User Guide

### Login

1. Open the application URL.
2. Enter a demo account.
3. Select **Sign in**.
4. The frontend calls `POST /api/login`, stores the token in memory, and loads server data.

Authenticated requests use:

```http
Authorization: Bearer <token>
```

### Dashboard

The dashboard shows today's sales, transaction count, product count, low-stock count, alerts, and recent sales. Administrators also see inventory value and payment totals for Cash, Mobile Money, and Card.

### Products

Administrators can add, edit, search, restock, and delete products. Product fields are:

- Name
- SKU
- Category
- Cost price
- Selling price
- Quantity
- Low-stock threshold
- Optional image

SKUs are unique, prices cannot be negative, and quantities/thresholds must be non-negative whole numbers.

### Alerts

- **Out of stock**: quantity is `0`
- **Low stock**: quantity is less than or equal to threshold
- **In stock**: quantity is above threshold

### Point of Sale

1. Search or filter products.
2. Add items to the cart.
3. Adjust quantities.
4. Choose Cash, Mobile Money, or Card.
5. Enter cash received when paying with Cash.
6. Select **Complete sale**.

The backend rechecks stock, calculates totals and change, decreases inventory, and records the sale.

### Saved orders

**Save order** stores the cart without reducing inventory. Opening an order rechecks stock and moves it to the cart. Inventory is reduced only when the sale is completed.

### Sales history

Administrators can search receipts by receipt ID, cashier, or payment method. A sale contains its date, cashier, payment, total, amount received, change, and line items.

### Product updates

Administrators can view opening stock, manual adjustments, restocks, and product deletions with timestamp, previous balance, quantity change, new balance, administrator, note, and action.

## 10. REST API

All responses are JSON. Every endpoint except login requires a valid bearer token.

### Authentication

#### `POST /api/login`

```json
{
  "username": "cashier",
  "password": "cashier123"
}
```

Returns:

```json
{
  "token": "session-token",
  "name": "Cashier",
  "role": "cashier"
}
```

#### `GET /api/me`

Returns the current authenticated session.

#### `POST /api/logout`

Removes the current in-memory session.

### Dashboard

#### `GET /api/dashboard`

Returns today's totals, product counts, low-stock products, recent sales, and administrator-only financial metrics.

#### `GET /api/categories`

Returns sorted categories currently assigned to products.

### Products

| Method | Endpoint | Permission | Purpose |
|---|---|---|---|
| GET | `/api/products` | Authenticated | List products |
| GET | `/api/products/{id}` | Authenticated | Get one product |
| POST | `/api/products` | Admin | Create product |
| PUT | `/api/products/{id}` | Admin | Update product |
| DELETE | `/api/products/{id}` | Admin | Delete product |
| POST | `/api/products/{id}/restock` | Admin | Add stock |

Create/update example:

```json
{
  "name": "Bottled Water",
  "sku": "SKU001",
  "category": "Drinks",
  "cost": 2,
  "selling": 3,
  "quantity": 10,
  "threshold": 5,
  "image": ""
}
```

Restock example:

```json
{
  "quantity": 20,
  "note": "Supplier delivery"
}
```

### Sales

| Method | Endpoint | Permission | Purpose |
|---|---|---|---|
| POST | `/api/sales` | Authenticated | Complete sale |
| GET | `/api/sales` | Admin | View sales history |

Sale request:

```json
{
  "payment": "Cash",
  "received": 50,
  "items": [
    { "id": 1, "quantity": 2 }
  ]
}
```

Payment must be exactly `Cash`, `Mobile Money`, or `Card`.

### Updates

#### `GET /api/updates`

Administrator-only stock-update history.

### Orders

| Method | Endpoint | Permission | Purpose |
|---|---|---|---|
| GET | `/api/orders` | Authenticated | List saved orders |
| POST | `/api/orders` | Authenticated | Save an order |
| POST | `/api/orders/{id}/open` | Authenticated | Reopen an order |
| DELETE | `/api/orders/{id}` | Authenticated | Delete an order |

Order request:

```json
{
  "items": [
    { "id": 1, "quantity": 2 }
  ]
}
```

## 11. Data Model

`data.json` contains:

```json
{
  "products": [],
  "sales": [],
  "updates": [],
  "orders": []
}
```

### Product

```json
{
  "id": 1,
  "name": "Bottled Water",
  "sku": "SKU001",
  "category": "Drinks",
  "cost": 2,
  "selling": 3,
  "quantity": 4,
  "threshold": 5,
  "image": ""
}
```

### Sale

```json
{
  "id": "SP-1700000000000",
  "date": "2026-11-11T16:00:00Z",
  "cashier": "Cashier",
  "payment": "Cash",
  "total": 6,
  "received": 10,
  "change": 4,
  "items": [
    { "id": 1, "name": "Bottled Water", "quantity": 2, "price": 3 }
  ]
}
```

### Stock update

```json
{
  "id": 1,
  "productId": 1,
  "product": "Bottled Water",
  "sku": "SKU001",
  "date": "2026-11-11T16:00:00Z",
  "previousStock": 4,
  "quantityAdded": 20,
  "newBalance": 24,
  "admin": "Admin",
  "note": "Supplier delivery",
  "action": "Stock update"
}
```

### Saved order

```json
{
  "id": "ORD-1700000000000",
  "date": "2026-11-11T16:00:00Z",
  "items": [
    { "id": 1, "name": "Bottled Water", "selling": 3, "quantity": 2 }
  ],
  "total": 6
}
```

## 12. Business Rules

- Product name and SKU are required.
- SKU values are unique, case-insensitively.
- Prices, quantities, and thresholds cannot be negative.
- Stock and restock quantities must be whole numbers.
- Restock quantity must be greater than zero.
- Sales require at least one item.
- Sale quantities must be positive and cannot exceed available stock.
- Cash received must be at least the total.
- Payment method must be recognized.
- Orders cannot be opened when stock is insufficient.
- Administrator endpoints reject cashier sessions.

## 13. Persistence, Backup, and Recovery

- `data.json` is created automatically on first startup.
- If it does not exist, six seed products are created.
- Writes use `data.json.tmp` and then replace `data.json`.
- The working directory determines where `data.json` is stored.
- Runtime data is ignored by Git.
- Sessions are lost when the server restarts.

Backup:

```powershell
Copy-Item data.json data-backup.json
```

Restore while the server is stopped:

```powershell
Copy-Item data-backup.json data.json -Force
```

## 14. Error Responses

```json
{
  "error": "Human-readable message"
}
```

| Status | Meaning |
|---:|---|
| 200 | Success |
| 204 | CORS preflight success |
| 400 | Invalid input or JSON |
| 401 | Missing/invalid session or credentials |
| 403 | Administrator permission required |
| 404 | Resource or endpoint not found |
| 409 | Duplicate SKU or insufficient stock |
| 500 | Unexpected server error |

## 15. Troubleshooting

### “Failed to fetch”

The backend is not running, the browser is using a different port, or the port is blocked. Start the server and open the matching URL:

```powershell
java -cp Backend StockPilotServer 8080 Frontend
```

Then open `http://localhost:8080/`.

### Port already in use

Use another port:

```powershell
java -cp Backend StockPilotServer 8091 Frontend
```

Open `http://localhost:8091/`.

### Missing styles or images

Start with `Frontend` as the second argument and access the app through Java, not as a local file.

### Login rejected

Use the exact demo credentials. Usernames are case-insensitive; passwords are case-sensitive.

### Data reset or missing

Check the directory from which the server was started. `data.json` is relative to that directory. Restore a backup if necessary.

### Compilation failure

Install/use a JDK and verify:

```powershell
javac -version
```

## 16. Security Notes

This implementation is for local demonstration and academic use. A production deployment should:

- Replace hard-coded credentials.
- Hash passwords.
- Use HTTPS.
- Use expiring and revocable sessions.
- Restrict CORS instead of allowing `*`.
- Add audit logging, rate limiting, and account lockout.
- Limit uploaded image sizes.
- Use a transactional database instead of a JSON file.
- Avoid exposing detailed internal errors.

## 17. Testing Checklist

### Startup

- [ ] Java compilation succeeds.
- [ ] Server starts on the selected port.
- [ ] Login page loads.

### Authentication

- [ ] Admin login succeeds.
- [ ] Cashier login succeeds.
- [ ] Invalid credentials are rejected.
- [ ] Logout returns to login.

### Administrator

- [ ] Add, edit, restock, search, and delete products.
- [ ] View sales history.
- [ ] View product updates.
- [ ] Confirm duplicate SKU and invalid values are rejected.

### Cashier

- [ ] View dashboard and alerts.
- [ ] Add products to cart.
- [ ] Save, reopen, and delete orders.
- [ ] Complete Cash, Mobile Money, and Card sales.
- [ ] Confirm insufficient stock and insufficient cash are rejected.

### Persistence

- [ ] Restarting the backend preserves products, sales, updates, and orders.
- [ ] `data.json` is not committed to Git.

## 18. Known Limitations

- Authentication is intended for local demonstration.
- Sessions disappear on server restart.
- Data is stored in one local JSON file.
- There is no multi-server transaction coordination.
- There is no password-management screen.
- Product images can increase the data-file size.
- There is no automated test suite currently in the repository.
- Icons depend on the Lucide CDN.

## 19. Maintenance Guide

When adding an API feature:

1. Add the route in `StockPilotServer.route`.
2. Validate input in the backend.
3. Persist changes with `save()`.
4. Add the matching `apiRequest` call in `Frontend/app.js`.
5. Reload server state after mutations.
6. Update this document with endpoint, permission, request, and response details.
7. Test both administrator and cashier access.

When changing the data model:

1. Update seed data.
2. Update load/save behavior.
3. Preserve compatibility with existing `data.json` files where possible.
4. Update the Data Model and backup sections.

## 20. Quick Reference

```powershell
javac Backend\StockPilotServer.java
java -cp Backend StockPilotServer 8080 Frontend
start http://localhost:8080/
```

```text
admin   / admin123
cashier / cashier123
```
