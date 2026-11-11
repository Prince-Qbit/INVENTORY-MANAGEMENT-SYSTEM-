**SYSTEM DESIGN**

1. **What the System Does**

The system helps a shop manage products and sales. Users can add products, update or delete them, search for products, record sales, view low-stock items, and calculate the total value of inventory.

2. **System Components**

User Interface (HTML \+ CSS)

        ↓

Application Logic

        ↓

Database

* ***User Interface***: The UI contains pages for managing products, recording sales, and viewing reports.  
* ***Application Logic***: This component Checks information, updates stock after sales, and calculates reports.  
* ***Database***: The database stores products, sales, and stock details.




3. **Main Functionalities**  
* ***Add product***: The user enters the product name, stock keeping unit (SKU), category, cost price, selling price, quantity, and reorder level.  
* ***Update product***: The user can edit product details or stock quantity.  
* ***Delete product***: The user can remove a product from the system.  
* ***Search product***: The user can search using the product name, SKU, or category.  
* ***Record sale***: The user selects a product and enters the quantity sold. The system checks that enough stock is available, then reduces the stock automatically.  
* ***View low-stock items***: The system shows products whose stock is at or below the reorder level.  
* ***Calculate inventory value***: The system adds up the value of all products currently in stock.  
    
4. **Database Design**

The system will use three main tables:

* ***Products***: Stores product details such as product ID, name, SKU, category, cost price, selling price, quantity in stock, and reorder level.  
* ***Sales***: Stores sale information such as sale ID, sale date, total amount, and payment method.  
* ***Sale Items***: Stores the products included in each sale, including product ID, quantity sold, and unit price.

One sale can contain many products, so each product sold is saved as a sale item.

5. **How a Sale Works**

User selects a product

   ↓

User enters quantity

   ↓

System checks available stock

   ↓

If stock is enough:

* Save the sale  
* Reduce product stock  
* Show success message

If stock is not enough:

* Show “Insufficient stock”

For example, if there are 10 bags of rice and a customer buys 3, the system updates the stock to 7\.

6. **Low-Stock Rule**

A product is considered low in stock when:

Quantity in Stock \< Reorder Level

For example, if a product has 5 items left and its reorder level is 10, it will appear in the low-stock report.

7. **Inventory Value**

Inventory Value \= Quantity in Stock × Cost Price

For example, if the shop has 20 products that cost GHS 50 each, the inventory value is GHS 1,000.

8. **User Interface Pages**  
* ***Dashboard***: Shows total products, low-stock items, and total inventory value.  
* ***Products page***: Used to add, update, delete, and search products.  
* ***Record Sale page***: Used to sell products and automatically update stock.  
* ***Low Stock page***: Shows products that need restocking.  
* ***Inventory Value page***: Shows the total value of all stock

9. **Summary**

The Inventory Management System will allow users to manage products through a simple HTML and CSS interface. It will store product and sales information in a database, automatically reduce stock after each sale, show low-stock products, and calculate the total value of inventory.
