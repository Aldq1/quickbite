import com.example.quickbite.models.Order
import com.example.quickbite.models.OrderItem
import com.example.quickbite.models.OrderStatus
import com.example.quickbite.models.TableStatus

// ── Menu product model ────────────────────────────────────────────────────────

data class MenuItem(
    val name: String,
    val description: String,
    val price: Double,
    val category: String
)

// ── Premium menu items ────────────────────────────────────────────────────────

val mockMenuItems: List<MenuItem> = listOf(
    MenuItem(
        name        = "Burger QuickBite Epic",
        description = "Carne de vită 200g, cheddar aged, bacon crispy, sos special QB, salată & roșii proaspete",
        price       = 42.00,
        category    = "Burgeri"
    ),
    MenuItem(
        name        = "Pizza Quattro Formaggi",
        description = "Mozzarella, gorgonzola, parmezan, pecorino, blat crocant pe vatră de piatră",
        price       = 49.00,
        category    = "Pizza"
    ),
    MenuItem(
        name        = "Cartofi cu Parmezan & Trufe",
        description = "Cartofi belgieni prăjiți la dublu, sos de parmezan, ulei de trufe, rozmarin",
        price       = 24.00,
        category    = "Garnituri"
    ),
    MenuItem(
        name        = "Limonadă cu Mentă & Ghimbir",
        description = "Lămâie stoarsă, sirop de mentă, ghimbir proaspăt, apă carbogazoasă — 500 ml",
        price       = 16.00,
        category    = "Băuturi"
    ),
    MenuItem(
        name        = "Tiramisu della Casa",
        description = "Rețetă originală italiană — piscoturi, mascarpone, espresso, cacao Valrhona",
        price       = 28.00,
        category    = "Deserturi"
    ),
    MenuItem(
        name        = "Somon Gravlax cu Avocado",
        description = "File de somon marinat în sare & zahăr, cremă de avocado, capere, lemon zest",
        price       = 56.00,
        category    = "Preparate Pește"
    ),
    MenuItem(
        name        = "Sparanghel la Grătar",
        description = "Sparanghel verde cu sos hollandaise, ou poché, chipsuri de șuncă Praga",
        price       = 32.00,
        category    = "Intrări"
    ),
    MenuItem(
        name        = "Flat White",
        description = "Dublu espresso, lapte de oat textura mătăsoasă, latte art inclus",
        price       = 14.00,
        category    = "Cafea"
    ),
)

// ── Mock orders (pre-populate 2 occupied tables for demo) ─────────────────────

private val now = System.currentTimeMillis()

val mockOrders: List<Order> = listOf(
    // Table 3 – COOKING (kitchen is working on it)
    Order(
        id           = "mock-order-01",
        restaurantId = "demo-restaurant",
        tableNumber  = 3,
        items        = listOf(
            OrderItem(name = "Burger QuickBite Epic",          category = "Burgeri",    quantity = 2),
            OrderItem(name = "Cartofi cu Parmezan & Trufe",    category = "Garnituri",  quantity = 2),
            OrderItem(name = "Limonadă cu Mentă & Ghimbir",   category = "Băuturi",    quantity = 3),
        ),
        totalPrice   = 164.00,
        status       = OrderStatus.COOKING,
        timestamp    = now - 8 * 60_000L   // 8 minutes ago
    ),
    // Table 7 – PENDING (just arrived, awaiting kitchen)
    Order(
        id           = "mock-order-02",
        restaurantId = "demo-restaurant",
        tableNumber  = 7,
        items        = listOf(
            OrderItem(name = "Pizza Quattro Formaggi",         category = "Pizza",      quantity = 1),
            OrderItem(name = "Somon Gravlax cu Avocado",       category = "Pește",      quantity = 1),
            OrderItem(name = "Tiramisu della Casa",            category = "Deserturi",  quantity = 2),
            OrderItem(name = "Flat White",                     category = "Cafea",      quantity = 2),
        ),
        totalPrice   = 161.00,
        status       = OrderStatus.PENDING,
        timestamp    = now - 2 * 60_000L   // 2 minutes ago
    ),
    // Table 7 – second order on same table (drinks ordered earlier)
    Order(
        id           = "mock-order-03",
        restaurantId = "demo-restaurant",
        tableNumber  = 7,
        items        = listOf(
            OrderItem(name = "Sparanghel la Grătar",           category = "Intrări",    quantity = 2),
        ),
        totalPrice   = 64.00,
        status       = OrderStatus.DELIVERED,
        timestamp    = now - 15 * 60_000L  // 15 minutes ago
    ),
)

// ── Mock table statuses ───────────────────────────────────────────────────────

val mockTableStatuses: Map<Int, String> = buildMap {
    for (i in 1..20) put(i, TableStatus.FREE)
    put(3, TableStatus.OCCUPIED)
    put(7, TableStatus.OCCUPIED)
}
