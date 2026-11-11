# INVENTORY-MANAGEMENT-SYSTEM-
A simple Inventory Management System developed as a mid-semester Object-Oriented Programming project using Java (backend) and HTML/CSS (frontend).


## PROJECT DESCRIPTION 

The Inventory Management System is a team-based academic project that demonstrates core Object-Oriented Programming concepts such as classes, objects, encapsulation, and modular design.  

It provides basic inventory management features suitable for a small store or shop.


## FEATURES 

- Add, update, and delete products
- Search for products
- Record sales and automatically update stock
- View low-stock items
- Calculate total inventory value
- Simple and clean user interface (HTML + CSS)



## TECH STACK

**Frontend:**
- HTML
- CSS
- JavaScript (optional)

**Backend:**
- Java

**Other:**
- Git & GitHub (Version Control)

## RUN THE CONNECTED APPLICATION

See the complete [StockPilot system documentation](Documentation.md) for architecture, user workflows, API reference, data model, permissions, troubleshooting, and maintenance guidance.

The Java server serves the frontend and exposes the REST API used by `Frontend/app.js`.

```powershell
javac Backend\StockPilotServer.java
java -cp Backend StockPilotServer 8080 Frontend
```

Open `http://localhost:8080/` in a browser. The demo accounts are:

- Admin: `admin` / `admin123`
- Cashier: `cashier` / `cashier123`

Inventory, sales, updates, and saved orders are persisted by the backend in `data.json`.


## FOLDER STRUCTURE 

```text
inventory-management-system/
│
├── Frontend/
│   ├── CSS/
│   ├── JS/
│   ├── Pages/
│   └── Assets/
│
├── Backend/
│   ├── Src/
│   │   ├── model/
│   │   ├── service/
│   │   ├── dao/
│   │   └── util/
│   └── Main/
│
├── Documentation/
│   ├── Requirements/
│   ├── Diagrams/
│   ├── User-Stories/
│   └── Meeting-Notes/
│
├── System Design/
│   ├── Wireframes/
│   ├── Mockups/
│   └── Style-Guide/
│
├── Tests/
├── README.md
└── .gitignore
```
