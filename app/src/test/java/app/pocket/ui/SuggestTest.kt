package app.pocket.ui

import app.pocket.data.Category
import app.pocket.data.EXPENSE
import app.pocket.data.Record
import app.pocket.data.Suggest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SuggestTest {
    private val past = listOf(
        Record(1, note = "Reliance Stores milk", catId = 10),
        Record(2, note = "reliance fresh veg", catId = 10),
        Record(3, note = "Reliance Digital TV", catId = 20),
    )

    @Test fun mostUsedCategoryWins() = assertEquals(10L, Suggest.category("RELIANCE STORES", past))
    @Test fun unknownPayee() = assertNull(Suggest.category("Swiggy", past))
    @Test fun genericWordsIgnored() = assertNull(Suggest.category("Stores Ltd", past))

    private val cats = listOf(Category(1, "Food", EXPENSE, 0), Category(2, "Bills", EXPENSE, 0), Category(10, "Groceries", EXPENSE, 0), Category(20, "Gadgets", EXPENSE, 0))

    @Test fun historyBeatsBrandList() = assertEquals(10L, Suggest.category("Reliance Smart", past, cats))
    @Test fun brandListResolvesToUserCategory() = assertEquals(1L, Suggest.category("Swiggy", emptyList(), cats))
    @Test fun learnedChoiceWins() = assertEquals(2L, Suggest.category("Reliance Smart", past, cats, learned = 2L))
    @Test fun learnedChoiceOfDeletedCategoryIsIgnored() = assertEquals(10L, Suggest.category("Reliance Smart", past, cats, learned = 99L))
    @Test fun keyIgnoresCaseAndPunctuation() = assertEquals(Suggest.key("Reddy  Biryani"), Suggest.key("reddy-biryani"))
    private val filed = listOf("Reddy Biryani" to 1L, "Reliance Smart" to 1L, "Apollo Pharmacy" to 3L)

    @Test fun misspellingMatches() = assertEquals(1L, Suggest.similar("Reddy Briyani", filed))
    @Test fun longerNameMatches() = assertEquals(1L, Suggest.similar("Reliance Smart Bazaar", filed))
    @Test fun differentNameDoesNotMatch() = assertNull(Suggest.similar("Ola Cabs", filed))
    @Test fun tinyNamesNeverMatch() = assertNull(Suggest.similar("SP", listOf("SP" to 1L)))
    @Test fun similarityUsesLearnedPayees() = assertEquals(1L, Suggest.category("Reddy Briyani Hotel", emptyList(), cats, learnedAll = listOf("reddybiryani" to 1L)))
    @Test fun missingCategoryGivesNoSuggestion() = assertNull(Suggest.category("Netflix", emptyList(), cats))

    // First 50: the names the models were benchmarked on. Last 20: written after the list was built.
    private val payees = listOf(
        "Reddy biryani" to "Food",
        "Sri Lakshmi Tiffins" to "Food",
        "Annapoorna Hotel" to "Food",
        "Aditya Milk Dairy" to "Food",
        "Reliance Smart" to "Food",
        "D Mart" to "Food",
        "Swiggy" to "Food",
        "Zomato" to "Food",
        "Chai Sutta Bar" to "Food",
        "Haldirams" to "Food",
        "Ola Cabs" to "Transportation",
        "Uber India" to "Transportation",
        "Rapido" to "Transportation",
        "IRCTC" to "Transportation",
        "KSRTC Bus" to "Transportation",
        "redBus" to "Transportation",
        "Apollo Pharmacy" to "Health",
        "Sri Balaji Medicals" to "Health",
        "Dr Reddy Clinic" to "Health",
        "Netmeds" to "Health",
        "Jio Prepaid Recharge" to "Telephone",
        "Airtel Payments" to "Telephone",
        "BESCOM Electricity" to "Bills",
        "Tata Power" to "Bills",
        "Hathway Broadband" to "Bills",
        "Indane Gas" to "Bills",
        "Reliance Digital" to "Electronics",
        "Croma" to "Electronics",
        "Myntra" to "Clothing",
        "Pothys Textiles" to "Clothing",
        "Max Fashion" to "Clothing",
        "BookMyShow" to "Entertainment",
        "PVR Cinemas" to "Entertainment",
        "Netflix" to "Entertainment",
        "Decathlon Sports" to "Sport",
        "Cult Fit" to "Sport",
        "Byju's" to "Education",
        "Sri Chaitanya School Fees" to "Education",
        "HP Petrol Pump" to "Car",
        "Indian Oil Fuel Station" to "Car",
        "Naturals Salon" to "Beauty",
        "Green Trends Unisex Salon" to "Beauty",
        "LIC Premium" to "Insurance",
        "Star Health Insurance" to "Insurance",
        "FirstCry" to "Baby",
        "Urban Ladder" to "Home",
        "Pepperfry" to "Home",
        "Ramesh Hardware" to "Home",
        "Amazon Pay Shopping" to "Shopping",
        "Flipkart" to "Shopping",
        "Blinkit" to "Food",
        "Zepto" to "Food",
        "Nykaa" to "Beauty",
        "Bharat Petroleum" to "Car",
        "Vodafone Idea" to "Telephone",
        "Lenskart" to "Health",
        "Ajio" to "Clothing",
        "Meesho" to "Shopping",
        "Spotify" to "Entertainment",
        "Policybazaar" to "Insurance",
        "Tata Play DTH" to "Bills",
        "Unacademy" to "Education",
        "Goibibo" to "Transportation",
        "Ikea" to "Home",
        "Hamleys" to "Baby",
        "Cultfit Gym" to "Sport",
        "Vijay Sales" to "Electronics",
        "Sri Venkateswara Bakery" to "Food",
        "Metro Cash" to "Transportation",
        "PharmEasy" to "Health"
    )

    @Test fun brandsAndWords() {
        val wrong = payees.filter { (p, c) -> Suggest.byWords(p) != c }
        assertEquals(emptyList<Pair<String, String>>(), wrong)
    }
}
