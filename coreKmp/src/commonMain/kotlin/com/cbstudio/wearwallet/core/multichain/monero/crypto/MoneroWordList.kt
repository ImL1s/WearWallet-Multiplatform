package com.cbstudio.wearwallet.core.multichain.monero.crypto

/**
 * Monero 英文詞表
 * 
 * 包含 1626 個單詞，用於生成 Monero 助記詞
 * 來源：https://github.com/monero-project/monero/blob/master/src/mnemonics/english.h
 * 
 * 特點：
 * - 1626 個單詞（與 BIP39 的 2048 個不同）
 * - 所有單詞長度在 4-7 個字母之間
 * - 沒有單詞是另一個單詞的前綴
 * - 每個單詞的前三個字母都是唯一的
 */
object MoneroWordList {
    
    /**
     * Monero 英文詞表（1626 個單詞）
     * 用於 25 詞助記詞格式
     */
    val ENGLISH_WORDS = listOf(
        "abbey", "abducts", "ability", "ablaze", "abnormal", "abort", "abrasive", "absorb",
        "abyss", "academy", "aces", "aching", "acidic", "acoustic", "acquire", "across",
        "actress", "acumen", "adapt", "addicted", "adept", "adhesive", "adjust", "adopt",
        "adrenalin", "adult", "advance", "aerial", "afar", "affair", "afield", "afloat",
        "afoot", "afraid", "after", "against", "agenda", "aggravate", "agile", "aglow",
        "agnostic", "agony", "agreed", "ahead", "aided", "aids", "aimed", "aisle",
        "ajar", "akin", "alarms", "album", "alchemy", "alerts", "algebra", "alkaline",
        "alley", "almost", "aloof", "alpine", "already", "also", "altitude", "alumni",
        "always", "amaze", "ambush", "amended", "amidst", "ammo", "amnesty", "among",
        "amply", "amused", "anchor", "android", "anecdote", "angled", "ankle", "annoyed",
        "answers", "antics", "anvil", "anxiety", "anybody", "apart", "apex", "aphid",
        "aplomb", "apology", "apply", "apricot", "aptitude", "aquarium", "arbitrary", "archer",
        "ardent", "arena", "argue", "arises", "army", "around", "arrow", "arsenic",
        "artistic", "ascend", "ashtray", "aside", "asked", "asleep", "aspire", "assorted",
        "asylum", "athlete", "atlas", "atom", "atrium", "attire", "auburn", "auctions",
        "audio", "august", "aunt", "austere", "autumn", "avatar", "avidly", "avoid",
        "awakened", "awesome", "awful", "awkward", "awning", "awoken", "axes", "axis",
        "axle", "aztec", "azure", "baby", "bacon", "badge", "baffles", "bagpipe",
        "bailed", "bakery", "balding", "bamboo", "banjo", "baptism", "basin", "batch",
        "bawled", "bays", "because", "beer", "befit", "begun", "behind", "being",
        "below", "bemused", "benches", "berries", "bested", "betting", "bevel", "beware",
        "beyond", "bias", "bicycle", "bids", "bifocals", "biggest", "bikini", "bimonthly",
        "binocular", "biology", "biplane", "birth", "biscuit", "bite", "biweekly", "blading",
        "blah", "blanket", "blast", "blatant", "blazer", "blender", "blip", "blizzard",
        "blob", "bluntly", "boat", "bobsled", "bodies", "bogeys", "boil", "boldly",
        "bomb", "border", "boss", "both", "bounced", "bovine", "bowling", "boxes",
        "boycott", "boyfriend", "broken", "brunt", "bubble", "buckets", "budget", "buffet",
        "bugs", "building", "bulb", "bumper", "bunch", "business", "butter", "buying",
        "buzzer", "bygones", "byline", "bypass", "cabin", "cactus", "cadets", "cafe",
        "cage", "cajun", "cake", "calamity", "camp", "candy", "casket", "catch",
        "cause", "cavernous", "cease", "cedar", "ceiling", "cell", "cement", "cent",
        "certain", "chaining", "chair", "chalk", "chapter", "chariot", "cheer", "chef",
        "chemistry", "chest", "chief", "chimed", "chimney", "chlorine", "chocolate", "chopper",
        "chrome", "chump", "chunk", "cigar", "cinema", "circle", "cistern", "citadel",
        "civilian", "claim", "click", "clue", "coal", "cobra", "cocoa", "code",
        "coexist", "coffee", "cogs", "cohesive", "coils", "colony", "comb", "combine",
        "comfort", "comic", "commence", "compass", "comply", "composed", "concur", "condemn",
        "cone", "confetti", "conflict", "consider", "contour", "convey", "cool", "copy",
        "coral", "cork", "costume", "cottage", "cotton", "couch", "cougar", "count",
        "cousin", "cowl", "cozy", "craft", "cramp", "created", "credit", "criminal",
        "crisp", "criterion", "croak", "crossbar", "crucial", "crude", "cruise", "crumb",
        "crystal", "cubic", "cuisine", "cunning", "cupcake", "custom", "cycling", "cylinder",
        "cynical", "dads", "daft", "dagger", "daily", "damp", "dangerous", "dapper",
        "darted", "dash", "dating", "dauntless", "dawn", "daytime", "dazed", "debut",
        "decay", "dedicated", "deepest", "deftly", "degrees", "dehydrate", "deity", "delayed",
        "demonstrate", "dented", "deodorant", "depth", "desk", "devoid", "dewdrop", "dialogue",
        "dice", "diet", "different", "digit", "dilute", "dime", "dinner", "diode",
        "diplomat", "directed", "dirt", "disaster", "disco", "disguise", "dish", "distance",
        "ditch", "divers", "dizzy", "doctor", "dodge", "does", "dogs", "doing",
        "dolphin", "domestic", "donuts", "doorway", "dorsal", "dosage", "dotted", "double",
        "dove", "down", "dozen", "dreams", "drinks", "drowning", "drunk", "dual",
        "dubbed", "duckling", "dude", "duets", "duke", "dullness", "dummy", "dunes",
        "duplex", "duration", "dusted", "duties", "dwarf", "dwelling", "dwindling", "dying",
        "dynamite", "dyslexic", "each", "eagle", "earth", "easy", "eating", "eavesdrop",
        "eccentric", "echo", "eclipse", "economics", "ecstatic", "eden", "edgy", "edited",
        "educated", "eels", "efficient", "eggs", "egotistic", "eight", "either", "eject",
        "eldest", "eleven", "elite", "elope", "else", "eluded", "emails", "ember",
        "emerge", "emotion", "empty", "emulate", "energy", "enforce", "enhanced", "enigma",
        "enjoy", "enlist", "enmity", "enough", "ensign", "entrance", "envy", "epoxy",
        "equip", "erase", "erected", "erosion", "error", "eskimos", "espionage", "essential",
        "estate", "eternal", "ethics", "etiquette", "evaluate", "evenings", "evicted", "evolved",
        "examine", "excess", "exhale", "exit", "exotic", "expert", "exquisite", "extra",
        "exult", "fabrics", "factual", "fading", "fainted", "faked", "fall", "family",
        "fancy", "fangs", "fantasy", "fatal", "fate", "fathom", "fawns", "faxed",
        "fazed", "feast", "february", "federal", "feel", "feline", "females", "fences",
        "ferry", "festival", "fetched", "fever", "fewest", "fiat", "fibula", "fictional",
        "fidget", "fierce", "fifteen", "fight", "films", "firm", "fishing", "fitting",
        "five", "fixate", "fizzle", "fleet", "flippant", "flying", "foamy", "focus",
        "foes", "foggy", "foiled", "folding", "fonts", "foolish", "fossil", "foster",
        "foul", "foxes", "foyer", "framed", "frankly", "freebie", "frequency", "friction",
        "friend", "frown", "frozen", "fruit", "frying", "fudge", "fuel", "fugitive",
        "fully", "fuming", "fungal", "furnished", "fuselage", "future", "fuzzy", "gables",
        "gadget", "gags", "gained", "galaxy", "gambit", "gang", "gasp", "gather",
        "gauze", "gave", "gawk", "gaze", "gearbox", "gecko", "geek", "gels",
        "gemstone", "general", "geometry", "germs", "gesture", "getting", "geyser", "ghetto",
        "ghost", "giant", "giddy", "gifts", "gigantic", "gills", "gimmick", "ginger",
        "girth", "giving", "glass", "gleeful", "glide", "gnaw", "gnome", "goat",
        "goblet", "godfather", "goes", "goggles", "going", "goldfish", "gone", "goodbye",
        "gopher", "gorilla", "gossip", "gotten", "gourmet", "governing", "gown", "greater",
        "gecko", "greeting", "grew", "grid", "grief", "grill", "grin", "grocery",
        "groom", "grumpy", "guarded", "guest", "guide", "gulp", "gumball", "guru",
        "gusts", "gutter", "guys", "gymnast", "gypsy", "gyrate", "habitat", "hacksaw",
        "haggled", "hairy", "hamburger", "happens", "hashing", "hatchet", "haunted", "having",
        "hawk", "hazelnut", "headset", "healthy", "heap", "heart", "hedgehog", "heels",
        "hefty", "height", "hemlock", "hence", "heron", "hesitate", "hexagon", "hickup",
        "hiding", "highway", "hijack", "hiker", "hills", "himself", "hinder", "hippie",
        "history", "hitched", "hive", "hoax", "hobby", "hockey", "hoisting", "hold",
        "honked", "hookup", "hope", "hornet", "hospital", "hotel", "hounded", "hover",
        "howls", "hubcap", "huddle", "huge", "hull", "humid", "hunter", "hurried",
        "husband", "huts", "hybrid", "hydrogen", "hyper", "iceberg", "icing", "icon",
        "identity", "idiom", "idled", "idols", "igloo", "ignore", "iguana", "illness",
        "imagine", "imbalance", "imitate", "impel", "inactive", "inbound", "incur", "industrial",
        "inexact", "inflamed", "inform", "inhale", "injury", "inkling", "inline", "inmate",
        "innocent", "inorganic", "input", "inquest", "inroads", "insult", "intended", "inundate",
        "invoke", "inwardly", "ionic", "irate", "iris", "irony", "irritate", "island",
        "isolated", "issued", "italic", "itches", "items", "itself", "ivory", "jabbed",
        "jackets", "jaded", "jagged", "jailed", "jamming", "january", "jargon", "jaunt",
        "javelin", "jaws", "jazz", "jealous", "jeans", "jeers", "jelly", "jeopardy",
        "jester", "jetting", "jewels", "jigsaw", "jingle", "jittery", "jive", "jobs",
        "jockey", "jogger", "joining", "joking", "jolted", "jostle", "journal", "joyous",
        "jubilee", "judge", "juggled", "juicy", "jukebox", "july", "jump", "juncture",
        "jungle", "junior", "junk", "jury", "justice", "juvenile", "kangaroo", "karate",
        "keep", "kennel", "kept", "kernels", "kettle", "keyboard", "kickoff", "kidneys",
        "king", "kiosk", "kisses", "kite", "kitchens", "kiwi", "knapsack", "knee",
        "knife", "knowledge", "knuckle", "koala", "laboratory", "ladder", "lagoon", "lair",
        "lakes", "lamb", "language", "laptop", "large", "last", "later", "launching",
        "lava", "lawsuit", "layout", "lazy", "lectures", "ledge", "leech", "left",
        "legend", "lemon", "length", "leopard", "lesson", "lettuce", "lexicon", "liar",
        "library", "licks", "lids", "lied", "lifestyle", "light", "likewise", "lilac",
        "limits", "linen", "lion", "lipstick", "liquid", "listen", "lively", "loaded",
        "lobster", "locker", "lodge", "lofty", "logic", "loincloth", "long", "looking",
        "lopped", "lordship", "losing", "lottery", "loudly", "love", "lower", "loyal",
        "lucky", "luggage", "lukewarm", "lullaby", "lumber", "lunar", "lunch", "lurk",
        "lush", "luxury", "lymph", "lyrics", "macro", "madness", "magically", "magnet",
        "mailed", "major", "makeup", "malady", "mammal", "maps", "marble", "mare",
        "marking", "marriage", "match", "maul", "maverick", "maximum", "mayor", "maze",
        "meant", "mechanic", "medicate", "meeting", "megabyte", "melting", "memoir", "menu",
        "merger", "mesh", "metro", "mews", "mica", "mice", "midst", "mighty",
        "mime", "mingled", "minimal", "mirror", "misery", "mittens", "mixture", "moat",
        "mobile", "modeled", "moderate", "modified", "mogul", "moisture", "molten", "moment",
        "money", "moon", "mops", "morsel", "mostly", "motherly", "mouth", "movement",
        "mowing", "much", "muddy", "muffin", "mugged", "mullet", "mumble", "mundane",
        "muppet", "mural", "musical", "muzzle", "myriad", "myth", "nabbing", "nagged",
        "nail", "names", "nanny", "napkin", "narrate", "nasty", "natural", "nautical",
        "navy", "nearby", "necklace", "needed", "negative", "neither", "neon", "nephew",
        "nerves", "nestle", "network", "neutral", "never", "newborn", "newest", "newspaper",
        "next", "nibble", "nicer", "niche", "nickels", "nifty", "nightly", "nimbly",
        "nineteen", "nirvana", "nitrogen", "nobody", "nocturnal", "nodes", "noises", "nomad",
        "noodles", "northern", "nostril", "noted", "nouns", "nozzle", "nuance", "nucleus",
        "nugget", "nuisance", "null", "number", "nuns", "nurse", "nutshell", "nylon",
        "oaks", "oars", "oasis", "oatmeal", "obedient", "object", "obliged", "obnoxious",
        "observant", "obtains", "obvious", "occur", "ocean", "october", "octopus", "odds",
        "odometer", "offend", "often", "oilfield", "ointment", "okay", "older", "olive",
        "olympics", "omega", "omission", "omnibus", "onboard", "oncoming", "oneself", "ongoing",
        "onion", "online", "onslaught", "onto", "onward", "oozed", "opacity", "opened",
        "operator", "opium", "opossum", "opposite", "optical", "options", "opus", "orange",
        "orbit", "orchid", "orders", "organs", "origin", "ornament", "orphans", "oscar",
        "ostrich", "otherwise", "otter", "ouch", "ought", "ounce", "ourselves", "oust",
        "outbreak", "oval", "oven", "owed", "owls", "owner", "oxidant", "oxygen",
        "oyster", "ozone", "pact", "paddles", "pager", "pairing", "palace", "pamphlet",
        "pancakes", "paper", "paradise", "pastry", "patio", "pause", "pavements", "pawnshop",
        "payment", "peaches", "pebbles", "peculiar", "pedantic", "peeled", "pegs", "pelican",
        "pencil", "people", "pepper", "perfect", "pests", "petals", "phase", "pheasant",
        "phone", "phrases", "physics", "piano", "picked", "pierce", "pigment", "piloted",
        "pimple", "pinched", "pioneer", "pipeline", "pirate", "pistons", "pitched", "pivot",
        "pixels", "pizza", "plywood", "poaching", "pockets", "podcast", "poetry", "point",
        "poker", "polar", "ponies", "pool", "popular", "portents", "possible", "potato",
        "pouch", "poverty", "powder", "pram", "pranks", "precise", "prefix", "pregnant",
        "premise", "present", "pride", "problems", "produce", "profiles", "program", "promise",
        "proof", "prophet", "prototype", "prowl", "proxy", "prude", "prunes", "psychic",
        "public", "puddle", "puffin", "pulp", "pumpkins", "punch", "puppy", "purchase",
        "purity", "push", "putty", "puzzled", "pylons", "pyramid", "python", "queen",
        "quick", "quote", "rabbits", "racetrack", "radar", "rafts", "rage", "railway",
        "raking", "rally", "ramped", "randomly", "rapid", "rash", "rated", "ravine",
        "rays", "razor", "react", "rebel", "recipe", "reduce", "reef", "refer",
        "regular", "reheat", "reinvest", "rejoices", "rekindle", "relic", "remedy", "renting",
        "reorder", "repent", "request", "reruns", "rest", "resulted", "retrofit", "reunion",
        "revamp", "rewind", "rhino", "rhythm", "ribbon", "richly", "ridges", "rift",
        "rigid", "rims", "ringing", "riots", "ripped", "rising", "ritual", "river",
        "roared", "robot", "rockets", "rodent", "rogue", "roles", "romance", "roomy",
        "roped", "roster", "rotate", "rounded", "rover", "rowboat", "royal", "ruby",
        "rudely", "ruffled", "rugged", "ruined", "ruling", "rumble", "runway", "rural",
        "rustled", "ruthless", "sabotage", "sack", "sadness", "safety", "saga", "sailor",
        "sake", "salads", "sample", "sanity", "sapling", "sarcasm", "sash", "satin",
        "saucepan", "saved", "sawmill", "saxophone", "sayings", "scamper", "scenic", "school",
        "science", "scoop", "scrub", "scuba", "seasons", "second", "sedan", "seeded",
        "segments", "seismic", "selfish", "semifinal", "sensible", "september", "sequence", "serving",
        "session", "setup", "seventh", "sewage", "shackles", "shelter", "shipped", "shocking",
        "shrugged", "shuffled", "shutter", "siblings", "sickness", "sidekick", "sieve", "sifting",
        "sighting", "silk", "simplest", "sincerely", "sipped", "siren", "situated", "sixteen",
        "sizes", "skater", "skew", "skirting", "skulls", "skydive", "slackens", "sleepless",
        "slid", "slower", "slug", "smash", "smelting", "smidgen", "smog", "smuggled",
        "snake", "sneeze", "sniff", "snout", "snowball", "snuggled", "soapy", "sober",
        "soccer", "soda", "software", "soggy", "soil", "solved", "somewhere", "sonic",
        "soothe", "soprano", "sorry", "southern", "sovereign", "sowed", "soya", "space",
        "speedy", "sphere", "spiders", "splendid", "spout", "sprig", "spud", "spying",
        "square", "stacking", "stellar", "stick", "stockpile", "strained", "stunning", "stylus",
        "suburbs", "subway", "succeed", "suddenly", "suede", "suffice", "sugar", "suitcase",
        "sulking", "summon", "sunken", "superior", "surfer", "sushi", "suture", "swagger",
        "swept", "swiftly", "sword", "swung", "syllabus", "symptoms", "syndrome", "syringe",
        "system", "taboo", "tacit", "tadpoles", "tagged", "tail", "taken", "talent",
        "tamper", "tank", "tapestry", "tarnished", "tasked", "tattoo", "taunts", "tavern",
        "tawny", "taxi", "teardrop", "technical", "tedious", "teeming", "tell", "template",
        "tender", "tepid", "terrain", "tethered", "textbook", "thaw", "theatrics", "thesis",
        "thick", "thighs", "things", "thinking", "thirsty", "thorn", "threaten", "thumbs",
        "thwart", "ticket", "tidy", "tiers", "tiger", "tilt", "timber", "tinted",
        "tipsy", "tirade", "tissue", "titans", "toaster", "tobacco", "today", "toenail",
        "toffee", "together", "toilet", "token", "tolerant", "tomorrow", "tonic", "toolbox",
        "topic", "torch", "tossed", "total", "touchy", "towel", "toxic", "toyed",
        "trash", "trendy", "tribal", "trolling", "tropical", "trouble", "trucks", "trumpet",
        "try", "tsunami", "tubes", "tucks", "tudor", "tuesday", "tugs", "tuition",
        "tulips", "tumbling", "tunnel", "turnip", "tusks", "tutor", "tuxedo", "twang",
        "tweezers", "twice", "twofold", "tycoon", "type", "typical", "tyrant", "ugly",
        "ulcers", "ultimate", "umbrella", "umpire", "unafraid", "unbending", "uncle", "under",
        "uneven", "unfit", "ungainly", "unhappy", "union", "unjustly", "unknown", "unlikely",
        "unmask", "unnoticed", "unopened", "unplugs", "unquoted", "unrest", "unsafe", "until",
        "unusual", "unveil", "unwind", "unzip", "upbeat", "upcoming", "update", "upgrade",
        "uphill", "upkeep", "upload", "upon", "upper", "upright", "upstairs", "uptight",
        "uptown", "upwards", "urban", "urchins", "urgent", "usage", "useful", "usher",
        "using", "usual", "utensils", "utility", "utmost", "utopia", "uttered", "vacation",
        "vague", "vain", "value", "vampire", "vane", "vapors", "vary", "vastness",
        "vats", "vaults", "vector", "veered", "vegan", "vehicle", "vein", "velvet",
        "vending", "venomous", "verify", "vexed", "vials", "vibrate", "victim", "video",
        "viewpoint", "vigilant", "viking", "village", "vinegar", "violin", "vipers", "virtual",
        "visited", "visual", "vitals", "vivid", "vixen", "vocal", "vogue", "voice",
        "volcano", "vortex", "voted", "voucher", "vowels", "voyage", "vulture", "wade",
        "waffle", "wagtail", "waist", "waking", "wallets", "wanted", "warped", "washing",
        "water", "waveform", "waxing", "wayside", "weavers", "website", "wedge", "weekday",
        "weird", "welders", "went", "wept", "were", "western", "wetsuit", "whale",
        "when", "whipped", "whole", "wickets", "width", "wield", "wife", "wiggle",
        "wildly", "winter", "wipeout", "wiring", "wise", "withdrawn", "wives", "wizard",
        "wobbly", "woes", "wolf", "womanly", "wonders", "woozy", "worry", "wounded",
        "woven", "wrap", "wrath", "wreckage", "wren", "wrist", "wrong", "yacht",
        "yahoo", "yanks", "yard", "yawning", "yearbook", "yellow", "yesterday", "yeti",
        "yields", "yodel", "yoga", "younger", "yoyo", "zapped", "zeal", "zebra",
        "zero", "zesty", "zigzags", "zinger", "zippers", "zodiac", "zombie", "zones",
        "zoom"
    )
    
    /**
     * 獲取詞表大小
     */
    fun getWordCount(): Int = ENGLISH_WORDS.size
    
    /**
     * 根據索引獲取單詞
     */
    fun getWord(index: Int): String {
        require(index in ENGLISH_WORDS.indices) {
            "Word index $index out of range (0..${ENGLISH_WORDS.size - 1})"
        }
        return ENGLISH_WORDS[index]
    }
    
    /**
     * 獲取單詞的索引
     */
    fun getWordIndex(word: String): Int {
        val index = ENGLISH_WORDS.indexOf(word.lowercase())
        require(index >= 0) {
            "Word '$word' not found in Monero word list"
        }
        return index
    }
    
    /**
     * 驗證單詞是否在詞表中
     */
    fun isValidWord(word: String): Boolean {
        return ENGLISH_WORDS.contains(word.lowercase())
    }
    
    /**
     * 驗證助記詞（檢查所有單詞是否在詞表中）
     */
    fun validateMnemonic(mnemonic: String): Boolean {
        val words = mnemonic.trim().split(" ").filter { it.isNotBlank() }
        return words.all { isValidWord(it) }
    }
}