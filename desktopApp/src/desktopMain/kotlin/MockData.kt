import com.example.quickbite.models.Order
import com.example.quickbite.models.OrderItem
import com.example.quickbite.models.OrderStatus
import com.example.quickbite.models.TableStatus

// ── Menu product model ────────────────────────────────────────────────────────

data class MenuItem(
    val name: String,
    val description: String,
    val price: Double,
    val category: String,
    val id: String = ""
)

// ── Premium menu items ────────────────────────────────────────────────────────

val mockMenuItems: List<MenuItem> = listOf(
    // ── Supe ─────────────────────────────────────────────────────────────────
    MenuItem("Ciorbă de Burtă",              "Supe",       23.00, "Burtă de vită, smântână, usturoi, oțet, ardei iute — rețetă tradițională"),
    MenuItem("Supă Cremă de Dovleac",        "Supe",       19.00, "Dovleac copt, ghimbir, lapte de cocos, semințe de dovleac prăjite"),
    MenuItem("Ciorbă Rădăuțeană",            "Supe",       25.00, "Piept de pui, smântână grasă, usturoi, morcov, leuștean proaspăt"),
    // ── Intrări ──────────────────────────────────────────────────────────────
    MenuItem("Sparanghel la Grătar",         "Intrări",    32.00, "Sparanghel verde, sos hollandaise, ou poché, chipsuri de șuncă Praga"),
    MenuItem("Bruschette cu Roșii & Burrata","Intrări",    26.00, "Pâine de casă prăjită, roșii cherry, burrata proaspătă, busuioc, ulei de măsline"),
    MenuItem("Cârnăciori de Casă la Grătar", "Intrări",    34.00, "Cârnăciori din porc & vită, muștar de Dijon, murături asortate"),
    // ── Salate ───────────────────────────────────────────────────────────────
    MenuItem("Salată Caesar cu Pui",         "Salate",     34.00, "Pui la grătar, salată romană, crutoane, parmezan ras, sos Caesar clasic"),
    MenuItem("Salată Grecească",             "Salate",     26.00, "Roșii, castravete, ceapă roșie, măsline Kalamata, brânză feta, oregano"),
    MenuItem("Salată cu Somon & Quinoa",     "Salate",     42.00, "Somon afumat la rece, quinoa, rucola, avocado, dressing de lămâie"),
    // ── Paste ────────────────────────────────────────────────────────────────
    MenuItem("Pasta Carbonara",              "Paste",      38.00, "Spaghetti, guanciale crocant, gălbenuș de ou, pecorino Romano, piper negru"),
    MenuItem("Penne Arrabiata",              "Paste",      32.00, "Sos de roșii San Marzano, usturoi, ardei iute, busuioc proaspăt, parmezan"),
    MenuItem("Tagliatelle Gorgonzola & Nucă","Paste",      42.00, "Gorgonzola dolce, smântână, nuci prăjite, rucola, reducție de vin alb"),
    // ── Burgeri ──────────────────────────────────────────────────────────────
    MenuItem("Burger QuickBite Epic",        "Burgeri",    42.00, "Carne de vită 200g, cheddar aged, bacon crispy, sos special QB, salată & roșii proaspete"),
    MenuItem("Burger Crispy Chicken",        "Burgeri",    38.00, "Piept de pui crocant, coleslaw, castraveciori murați, sos ranch, chifla brioche"),
    MenuItem("Burger Veggie Deluxe",         "Burgeri",    36.00, "Pateu de năut & sfeclă, hummus, roșii cherry, rucola, sos tahini"),
    // ── Pizza ─────────────────────────────────────────────────────────────────
    MenuItem("Pizza Margherita",             "Pizza",      35.00, "Sos de roșii artizanal, mozzarella fior di latte, busuioc proaspăt"),
    MenuItem("Pizza Quattro Formaggi",       "Pizza",      49.00, "Mozzarella, gorgonzola, parmezan, pecorino — blat crocant pe vatră de piatră"),
    MenuItem("Pizza Diavola",                "Pizza",      44.00, "Salam picant, ardei iute, mozzarella, sos de roșii, oregano"),
    // ── Grătar ───────────────────────────────────────────────────────────────
    MenuItem("Cotlet de Porc cu Ierburi",    "Grătar",     52.00, "Cotlet 280g marinat în rozmarin & cimbru, garnitură la alegere, muștar de casă"),
    MenuItem("Mușchi de Vită (250g)",        "Grătar",     89.00, "Vită Angus, gătit la preferință, sos de piper verde, cartofi rosti"),
    MenuItem("Pui la Grătar cu Lemon Herb",  "Grătar",     46.00, "Piept de pui 300g, marinadă de lămâie & ierburi, piure cremos, legume sezoniere"),
    // ── Pește ────────────────────────────────────────────────────────────────
    MenuItem("Somon Gravlax cu Avocado",     "Pește",      56.00, "File de somon marinat în sare & zahăr, cremă de avocado, capere, lemon zest"),
    MenuItem("File de Șalău la Tigaie",      "Pește",      48.00, "Șalău proaspăt, unt brun cu capere, piure de conopidă, spanac sauté"),
    // ── Garnituri ─────────────────────────────────────────────────────────────
    MenuItem("Cartofi cu Parmezan & Trufe",  "Garnituri",  24.00, "Cartofi belgieni prăjiți la dublu, sos de parmezan, ulei de trufe, rozmarin"),
    MenuItem("Piure Cremos de Cartofi",      "Garnituri",  14.00, "Cartofi Agria, unt 82%, lapte integral, nucșoară"),
    MenuItem("Orez cu Legume Sezoniere",     "Garnituri",  13.00, "Orez basmati, dovlecei, morcov, ardei gras, sos de soia"),
    // ── Deserturi ─────────────────────────────────────────────────────────────
    MenuItem("Tiramisu della Casa",          "Deserturi",  28.00, "Rețetă originală italiană — piscoturi, mascarpone, espresso, cacao Valrhona"),
    MenuItem("Pancakes cu Fructe de Pădure", "Deserturi",  24.00, "Pancakes pufoase, compot de zmeură & afine, frișcă naturală, miere de salcâm"),
    MenuItem("Prăjitură de Ciocolată",       "Deserturi",  22.00, "Coulant de ciocolată 70%, înghețată de vanilie bourbon, caramel sărat"),
    // ── Băuturi ──────────────────────────────────────────────────────────────
    MenuItem("Limonadă cu Mentă & Ghimbir",  "Băuturi",    16.00, "Lămâie stoarsă, sirop de mentă, ghimbir proaspăt, apă carbogazoasă — 500 ml"),
    MenuItem("Suc de Portocale Proaspăt",    "Băuturi",    16.00, "Stors la comandă din portocale siciliene — 300 ml"),
    MenuItem("Apă Minerală Borsec",          "Băuturi",     8.00, "0.5L — plată sau carbogazoasă"),
    MenuItem("Cola / Fanta / Sprite",        "Băuturi",    10.00, "Doză 330 ml"),
    // ── Cafea ─────────────────────────────────────────────────────────────────
    MenuItem("Espresso",                     "Cafea",      10.00, "Blend arabica 100%, extracție 25 ml, crema densă"),
    MenuItem("Cappuccino",                   "Cafea",      13.00, "Espresso dublu, lapte textura mătăsoasă, spumă de lapte fină"),
    MenuItem("Flat White",                   "Cafea",      14.00, "Dublu espresso, lapte de oat textură mătăsoasă, latte art inclus"),
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
