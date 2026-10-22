package app.pocket.data

/**
 * Guesses a category for a payee: first from how the user filed similar past expenses, then from a built-in list of
 * Indian merchant brands and everyday words ("pharmacy", "salon", "swiggy"). Both only suggest; the user confirms.
 */
object Suggest {
    private val skip = setOf("the", "and", "store", "stores", "shop", "mart", "pvt", "ltd", "payment", "paid", "upi")
    private fun words(s: String) = s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 3 && it !in skip }.toSet()

    /** Most-used category among past expenses whose note shares a word with [payee]; null if none. */
    fun category(payee: String, past: List<Record>): Long? {
        val w = words(payee)
        if (w.isEmpty()) return null
        return past.filter { it.type == EXPENSE && it.catId != 0L && words(it.note).any { x -> x in w } }
            .groupingBy { it.catId }.eachCount().maxByOrNull { it.value }?.key
    }

    /** Stable key for a payee name: lowercase letters and digits only, so "Reddy  Biryani" and "reddy-biryani" match. */
    fun key(payee: String) = payee.lowercase().filter { it.isLetterOrDigit() }

    /**
     * Order: what the user last chose for this exact payee ([learned]), then payees spelled almost the same ([similar]),
     * then past notes sharing a word, then [byWords] resolved against the user's own expense categories (by name).
     */
    fun category(payee: String, past: List<Record>, cats: List<Category>, learned: Long? = null, learnedAll: List<Pair<String, Long>> = emptyList()): Long? {
        val valid = cats.filter { it.type == EXPENSE }.map { it.id }.toSet()
        learned?.takeIf { it in valid }?.let { return it }
        similar(payee, past.filter { it.type == EXPENSE && it.catId in valid }.map { it.note to it.catId } + learnedAll.filter { it.second in valid })?.let { return it }
        category(payee, past)?.takeIf { it in valid }?.let { return it }
        return byWords(payee)?.let { name -> cats.firstOrNull { it.type == EXPENSE && it.name.equals(name, true) }?.id }
    }

    private fun grams(s: String): Set<String> {
        val k = key(s)
        return if (k.length < 3) setOf(k) else (0..k.length - 3).map { k.substring(it, it + 3) }.toSet()
    }

    /**
     * Spelling match: compares 3-letter chunks of the payee with every filed (text, category) pair, so "Reddy Briyani",
     * "REDDY BIRYANI HOTEL" and "Reddy Biryani" meet. Neighbours at 0.5+ vote by score; null if none is close enough.
     * ponytail: scans every record per payment (fine for thousands); index the chunks if history gets huge.
     */
    fun similar(payee: String, filed: List<Pair<String, Long>>): Long? {
        val a = grams(payee)
        if (a.isEmpty() || key(payee).length < 3) return null
        val votes = HashMap<Long, Double>()
        for ((text, cat) in filed) {
            val b = grams(text)
            if (b.isEmpty()) continue
            val common = a.intersect(b).size.toDouble()
            val dice = 2 * common / (a.size + b.size)
            val inside = common / a.size // the payee sits fully inside a longer note
            val score = if (inside >= 0.9 && a.size >= 5) maxOf(dice, 0.5) else dice
            if (score >= 0.5) votes[cat] = (votes[cat] ?: 0.0) + score
        }
        return votes.maxByOrNull { it.value }?.key
    }

    // First match wins, so the specific lists (insurance, telephone, bills) come before the broad ones (food, shopping).
    // Words of 5+ letters also match inside the squashed name ("reliancedigital", "bookmyshow"); shorter ones need a whole word.
    // Category names are Pocket's defaults; a renamed or deleted category simply gets no suggestion.
    private val rules: List<Pair<String, Set<String>>> = listOf(
        "Insurance" to "insurance premium lic policybazaar acko hdfclife iciciprulife maxlife bajajallianz starhealth",
        "Telephone" to "jio airtel vodafone vi bsnl recharge prepaid postpaid mtnl",
        "Bills" to "electricity bescom tatapower adani msedcl tneb tsspdcl tangedco kseb gas indane bharatgas hpgas water broadband hathway fibernet actfibernet wifi dth tatasky tataplay dishtv sundirect billdesk municipal maintenance rent",
        "Car" to "petrol diesel fuel bpcl hpcl iocl indianoil bharatpetroleum nayara garage servicecenter servicecentre tyre tyres carwash workshop automobile motors puncture",
        "Transportation" to "ola uber rapido irctc redbus abhibus metro ksrtc tsrtc apsrtc bmtc bus cab taxi auto railway railways flight indigo airindia spicejet makemytrip goibibo yatra fastag parking toll",
        "Beauty" to "salon parlour parlor spa beauty naturals lakme nykaa barber haircut unisex cosmetics makeover grooming",
        "Sport" to "decathlon gym cult cultfit fitness sports badminton cricket turf yoga swimming",
        "Education" to "school college university tuition byjus unacademy coursera udemy vedantu academy institute fees exam classes coaching nptel",
        "Entertainment" to "bookmyshow pvr inox cinema movie netflix hotstar primevideo spotify jiosaavn gaana youtube sonyliv zee5 gaming steam playstation theatre multiplex amusement",
        "Electronics" to "croma reliancedigital vijaysales electronics gadgets apple samsung oneplus boat laptop computers imagine",
        "Clothing" to "myntra ajio maxfashion pantaloons westside zudio trends textiles silks fashion apparel garments tailor boutique readymade zara lifestyle jockey levis raymond pothys saree",
        "Baby" to "firstcry babyoye baby kids toys hamleys diaper mothercare",
        "Home" to "urbanladder pepperfry ikea furniture hardware interiors homecentre hometown plumbing electrician paint paints asianpaints carpenter mattress sleepwell kitchenware utensils decor",
        "Health" to "pharmacy pharma medical medicals medico medplus apollo netmeds pharmeasy tata1mg 1mg clinic hospital diagnostics lab labs dental dentist doctor dr practo healthcare optical lenskart",
        "Food" to "swiggy zomato dominos pizza pizzahut kfc mcdonalds burger biryani hotel restaurant cafe chai tea coffee starbucks tiffin tiffins bakery sweets haldiram haldirams dairy milk kirana grocery groceries supermarket dmart bigbasket blinkit zepto instamart jiomart reliancesmart reliancefresh licious juice icecream bhavan mess canteen dhaba kitchen foods eatery chaat barbeque",
        "Shopping" to "amazon flipkart meesho snapdeal shopping bazaar bazar mall retail",
        "Tax" to "incometax gst tds challan itr",
    ).map { (cat, list) -> cat to list.split(" ").toSet() }

    /** Category name suggested by brand or keyword, or null. */
    fun byWords(payee: String): String? {
        val lower = payee.lowercase()
        val tokens = lower.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }.toSet()
        val squashed = lower.filter { it.isLetterOrDigit() }
        return rules.firstOrNull { (_, ws) -> ws.any { it in tokens || (it.length >= 5 && squashed.contains(it)) } }?.first
    }
}
