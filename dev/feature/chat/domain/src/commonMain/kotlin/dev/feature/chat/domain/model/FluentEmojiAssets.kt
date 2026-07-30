package dev.feature.chat.domain.model

/**
 * Microsoft Fluent Emoji 3D — **generatsiya qilingan jadval, qo'lda tahrirlanmasin.**
 *
 * Manba: <https://github.com/microsoft/fluentui-emoji> (MIT — tijoriy ishlatishga ruxsat,
 * atribut talab qilmaydi). Telegram stikerlaridan farqli o'laroq bularni tarqatish
 * huquqiy jihatdan xavfsiz (`CHAT_MEDIA_AND_CALLS_BACKEND.md` §4.4).
 *
 * Har bir guruh — `"<emoji>\t<papka>"` qatorlari, Unicode tartibida. Papka oldidagi `*`
 * — emojining teri rangi variantlari bor, ya'ni yo'lda qo'shimcha `Default` bo'g'ini
 * bo'ladi. URL [FluentEmoji] da yig'iladi.
 *
 * Jadval **tekshirilgan**: faqat fayl nomi hosil qilish qoidasiga mos keladigan (va shu
 * sababli URL'i albatta mavjud) papkalar kiritilgan.
 *
 * Yangilash: `dev/tools/fluent-emoji/generate.py`.
 */
internal object FluentEmojiAssets {

    /** Aynan shu commit'ga qadab qo'yilgan — `@main` ertaga jimgina o'zgarib ketardi. */
    const val CDN: String = "https://cdn.jsdelivr.net/gh/microsoft/fluentui-emoji@62ecdc0d7ca5c6df32148c169556bc8d3782fca4/assets"

    const val SMILEYS: String = """😀	Grinning face
😃	Grinning face with big eyes
😄	Grinning face with smiling eyes
😁	Beaming face with smiling eyes
😆	Grinning squinting face
😅	Grinning face with sweat
🤣	Rolling on the floor laughing
😂	Face with tears of joy
🙂	Slightly smiling face
🙃	Upside-down face
🫠	Melting face
😉	Winking face
😊	Smiling face with smiling eyes
😇	Smiling face with halo
🥰	Smiling face with hearts
😍	Smiling face with heart-eyes
🤩	Star-struck
😘	Face blowing a kiss
😗	Kissing face
☺️	Smiling face
😚	Kissing face with closed eyes
😙	Kissing face with smiling eyes
🥲	Smiling face with tear
😋	Face savoring food
😛	Face with tongue
😜	Winking face with tongue
🤪	Zany face
😝	Squinting face with tongue
🤑	Money-mouth face
🤗	Hugging face
🤭	Face with hand over mouth
🫢	Face with open eyes and hand over mouth
🫣	Face with peeking eye
🤫	Shushing face
🤔	Thinking face
🫡	Saluting face
🤐	Zipper-mouth face
🤨	Face with raised eyebrow
😐	Neutral face
😑	Expressionless face
😶	Face without mouth
🫥	Dotted line face
😶‍🌫️	Face in clouds
😏	Smirking face
😒	Unamused face
🙄	Face with rolling eyes
😬	Grimacing face
😮‍💨	Face exhaling
🤥	Lying face
🫨	Shaking face
🙂‍↔️	Head shaking horizontally
🙂‍↕️	Head shaking vertically
😌	Relieved face
😔	Pensive face
😪	Sleepy face
🤤	Drooling face
😴	Sleeping face
😷	Face with medical mask
🤒	Face with thermometer
🤕	Face with head-bandage
🤢	Nauseated face
🤮	Face vomiting
🤧	Sneezing face
🥵	Hot face
🥶	Cold face
🥴	Woozy face
😵	Knocked-out face
😵‍💫	Face with spiral eyes
🤯	Exploding head
🤠	Cowboy hat face
🥳	Partying face
🥸	Disguised face
😎	Smiling face with sunglasses
🤓	Nerd face
🧐	Face with monocle
😕	Confused face
🫤	Face with diagonal mouth
😟	Worried face
🙁	Slightly frowning face
☹️	Frowning face
😮	Face with open mouth
😯	Hushed face
😲	Astonished face
😳	Flushed face
🥺	Pleading face
🥹	Face holding back tears
😦	Frowning face with open mouth
😧	Anguished face
😨	Fearful face
😰	Anxious face with sweat
😥	Sad but relieved face
😢	Crying face
😭	Loudly crying face
😱	Face screaming in fear
😖	Confounded face
😣	Persevering face
😞	Disappointed face
😓	Downcast face with sweat
😩	Weary face
😫	Tired face
🥱	Yawning face
😤	Face with steam from nose
😡	Pouting face
😠	Angry face
🤬	Face with symbols on mouth
😈	Smiling face with horns
👿	Angry face with horns
💀	Skull
☠️	Skull and crossbones
💩	Pile of poo
🤡	Clown face
👹	Ogre
👺	Goblin
👻	Ghost
👽	Alien
👾	Alien monster
🤖	Robot
😺	Grinning cat
😸	Grinning cat with smiling eyes
😹	Cat with tears of joy
😻	Smiling cat with heart-eyes
😼	Cat with wry smile
😽	Kissing cat
🙀	Weary cat
😿	Crying cat
😾	Pouting cat
🙈	See-no-evil monkey
🙉	Hear-no-evil monkey
🙊	Speak-no-evil monkey
💌	Love letter
💘	Heart with arrow
💝	Heart with ribbon
💖	Sparkling heart
💗	Growing heart
💓	Beating heart
💞	Revolving hearts
💕	Two hearts
💟	Heart decoration
❣️	Heart exclamation
💔	Broken heart
❤️‍🔥	Heart on fire
❤️‍🩹	Mending heart
❤️	Red heart
🩷	Pink heart
🧡	Orange heart
💛	Yellow heart
💚	Green heart
💙	Blue heart
🩵	Light blue heart
💜	Purple heart
🤎	Brown heart
🖤	Black heart
🩶	Grey heart
🤍	White heart
💋	Kiss mark
💯	Hundred points
💢	Anger symbol
💥	Collision
💫	Dizzy
💦	Sweat droplets
💨	Dashing away
🕳️	Hole
💬	Speech balloon
👁️‍🗨️	Eye in speech bubble
🗨️	Left speech bubble
🗯️	Right anger bubble
💭	Thought balloon
💤	Zzz"""

    const val PEOPLE: String = """👋	*Waving hand
🤚	*Raised back of hand
🖐️	*Hand with fingers splayed
✋	*Raised hand
🖖	*Vulcan salute
🫱	*Rightwards hand
🫲	*Leftwards hand
🫳	*Palm down hand
🫴	*Palm up hand
🫷	*Leftwards pushing hand
🫸	*Rightwards pushing hand
👌	*Ok hand
🤌	*Pinched fingers
🤏	*Pinching hand
✌️	*Victory hand
🤞	*Crossed fingers
🫰	*Hand with index finger and thumb crossed
🤟	*Love-you gesture
🤘	*Sign of the horns
🤙	*Call me hand
👈	*Backhand index pointing left
👉	*Backhand index pointing right
👆	*Backhand index pointing up
🖕	*Middle finger
👇	*Backhand index pointing down
☝️	*Index pointing up
🫵	*Index pointing at the viewer
👍	*Thumbs up
👎	*Thumbs down
✊	*Raised fist
👊	*Oncoming fist
🤛	*Left-facing fist
🤜	*Right-facing fist
👏	*Clapping hands
🙌	*Raising hands
🫶	*Heart hands
👐	*Open hands
🤲	*Palms up together
🤝	Handshake
🙏	*Folded hands
✍️	*Writing hand
💅	*Nail polish
🤳	*Selfie
💪	*Flexed biceps
🦾	Mechanical arm
🦿	Mechanical leg
🦵	*Leg
🦶	*Foot
👂	*Ear
🦻	*Ear with hearing aid
👃	*Nose
🧠	Brain
🫀	Anatomical heart
🫁	Lungs
🦷	Tooth
🦴	Bone
👀	Eyes
👁️	Eye
👅	Tongue
👄	Mouth
🫦	Biting lip
👶	*Baby
🧒	*Child
👦	*Boy
👧	*Girl
🧑	*Person
👱	*Person blonde hair
👨	*Man
🧔	*Person beard
🧔‍♂️	*Man beard
🧔‍♀️	*Woman beard
👨‍🦰	*Man red hair
👨‍🦱	*Man curly hair
👨‍🦳	*Man white hair
👨‍🦲	*Man bald
👩	*Woman
👩‍🦰	*Woman red hair
🧑‍🦰	*Person red hair
👩‍🦱	*Woman curly hair
🧑‍🦱	*Person curly hair
👩‍🦳	*Woman white hair
🧑‍🦳	*Person white hair
👩‍🦲	*Woman bald
🧑‍🦲	*Person bald
👱‍♀️	*Woman blonde hair
👱‍♂️	*Man blonde hair
🧓	*Older person
👴	*Old man
👵	*Old woman
🙍	*Person frowning
🙍‍♂️	*Man frowning
🙍‍♀️	*Woman frowning
🙎	*Person pouting
🙎‍♂️	*Man pouting
🙎‍♀️	*Woman pouting
🙅	*Person gesturing no
🙅‍♂️	*Man gesturing no
🙅‍♀️	*Woman gesturing no
🙆	*Person gesturing ok
🙆‍♂️	*Man gesturing ok
🙆‍♀️	*Woman gesturing ok
💁	*Person tipping hand
💁‍♂️	*Man tipping hand
💁‍♀️	*Woman tipping hand
🙋	*Person raising hand
🙋‍♂️	*Man raising hand
🙋‍♀️	*Woman raising hand
🧏	*Person deaf
🧏‍♂️	*Man deaf
🧏‍♀️	*Woman deaf
🙇	*Person bowing
🙇‍♂️	*Man bowing
🙇‍♀️	*Woman bowing
🤦	*Person facepalming
🤦‍♂️	*Man facepalming
🤦‍♀️	*Woman facepalming
🤷	*Person shrugging
🤷‍♂️	*Man shrugging
🤷‍♀️	*Woman shrugging
🧑‍⚕️	*Health worker
👨‍⚕️	*Man health worker
👩‍⚕️	*Woman health worker
🧑‍🎓	*Student
👨‍🎓	*Man student
👩‍🎓	*Woman student
🧑‍🏫	*Teacher
👨‍🏫	*Man teacher
👩‍🏫	*Woman teacher
🧑‍⚖️	*Judge
👨‍⚖️	*Man judge
👩‍⚖️	*Woman judge
🧑‍🌾	*Farmer
👨‍🌾	*Man farmer
👩‍🌾	*Woman farmer
🧑‍🍳	*Cook
👨‍🍳	*Man cook
👩‍🍳	*Woman cook
🧑‍🔧	*Mechanic
👨‍🔧	*Man mechanic
👩‍🔧	*Woman mechanic
🧑‍🏭	*Factory worker
👨‍🏭	*Man factory worker
👩‍🏭	*Woman factory worker
🧑‍💼	*Office worker
👨‍💼	*Man office worker
👩‍💼	*Woman office worker
🧑‍🔬	*Scientist
👨‍🔬	*Man scientist
👩‍🔬	*Woman scientist
🧑‍💻	*Technologist
👨‍💻	*Man technologist
👩‍💻	*Woman technologist
🧑‍🎤	*Singer
👨‍🎤	*Man singer
👩‍🎤	*Woman singer
🧑‍🎨	*Artist
👨‍🎨	*Man artist
👩‍🎨	*Woman artist
🧑‍✈️	*Pilot
👨‍✈️	*Man pilot
👩‍✈️	*Woman pilot
🧑‍🚀	*Astronaut
👨‍🚀	*Man astronaut
👩‍🚀	*Woman astronaut
🧑‍🚒	*Firefighter
👨‍🚒	*Man firefighter
👩‍🚒	*Woman firefighter
👮	*Police officer
👮‍♂️	*Man police officer
👮‍♀️	*Woman police officer
🕵️	*Detective
🕵️‍♂️	*Man detective
🕵️‍♀️	*Woman detective
💂	*Guard
💂‍♂️	*Man guard
💂‍♀️	*Woman guard
🥷	*Ninja
👷	*Construction worker
👷‍♂️	*Man construction worker
👷‍♀️	*Woman construction worker
🫅	*Person with crown
🤴	*Prince
👸	*Princess
👳	*Person wearing turban
👳‍♂️	*Man wearing turban
👳‍♀️	*Woman wearing turban
👲	*Person with skullcap
🧕	*Woman with headscarf
🤵	*Person in tuxedo
🤵‍♂️	*Man in tuxedo
🤵‍♀️	*Woman in tuxedo
👰	*Person with veil
👰‍♂️	*Man with veil
👰‍♀️	*Woman with veil
🤰	*Pregnant woman
🫃	*Pregnant man
🫄	*Pregnant person
🤱	*Breast feeding
👩‍🍼	*Woman feeding baby
👨‍🍼	*Man feeding baby
🧑‍🍼	*Person feeding baby
👼	*Baby angel
🎅	*Santa claus
🤶	*Mrs claus
🧑‍🎄	*Mx claus
🦸	*Person superhero
🦸‍♂️	*Man superhero
🦸‍♀️	*Woman superhero
🦹	*Person supervillain
🦹‍♂️	*Man supervillain
🦹‍♀️	*Woman supervillain
🧙	*Person mage
🧙‍♂️	*Man mage
🧙‍♀️	*Woman mage
🧚	*Person fairy
🧚‍♂️	*Man fairy
🧚‍♀️	*Woman fairy
🧛	*Person vampire
🧛‍♂️	*Man vampire
🧛‍♀️	*Woman vampire
🧜	*Person merpeople
🧜‍♂️	*Man merpeople
🧜‍♀️	*Woman merpeople
🧝	*Person elf
🧝‍♂️	*Man elf
🧝‍♀️	*Woman elf
🧞	Person genie
🧞‍♂️	Man genie
🧞‍♀️	Woman genie
🧟	Person zombie
🧟‍♂️	Man zombie
🧟‍♀️	Woman zombie
🧌	Troll
💆	*Person getting massage
💆‍♂️	*Man getting massage
💆‍♀️	*Woman getting massage
💇	*Person getting haircut
💇‍♂️	*Man getting haircut
💇‍♀️	*Woman getting haircut
🚶	*Person walking
🚶‍♂️	*Man walking
🚶‍♀️	*Woman walking
🚶‍➡️	*Person walking facing right
🚶‍♀️‍➡️	*Woman walking facing right
🚶‍♂️‍➡️	*Man walking facing right
🧍	*Person standing
🧍‍♂️	*Man standing
🧍‍♀️	*Woman standing
🧎	*Person kneeling
🧎‍♂️	*Man kneeling
🧎‍♀️	*Woman kneeling
🧎‍➡️	*Person kneeling facing right
🧎‍♀️‍➡️	*Woman kneeling facing right
🧎‍♂️‍➡️	*Man kneeling facing right
🧑‍🦯	*Person with white cane
🧑‍🦯‍➡️	*Person with white cane facing right
👨‍🦯	*Man with white cane
👨‍🦯‍➡️	*Man with white cane facing right
👩‍🦯	*Woman with white cane
👩‍🦯‍➡️	*Woman with white cane facing right
🧑‍🦼	*Person in motorized wheelchair
🧑‍🦼‍➡️	*Person in motorized wheelchair facing right
👨‍🦼	*Man in motorized wheelchair
👨‍🦼‍➡️	*Man in motorized wheelchair facing right
👩‍🦼	*Woman in motorized wheelchair facing right
🧑‍🦽	*Person in manual wheelchair
🧑‍🦽‍➡️	*Person in manual wheelchair facing right
👨‍🦽	*Man in manual wheelchair
👨‍🦽‍➡️	*Man in manual wheelchair facing right
👩‍🦽	*Woman in manual wheelchair
👩‍🦽‍➡️	*Woman in manual wheelchair facing right
🏃	*Person running
🏃‍♂️	*Man running
🏃‍♀️	*Woman running
🏃‍➡️	*Person running facing right
🏃‍♀️‍➡️	*Woman running facing right
🏃‍♂️‍➡️	*Man running facing right
💃	*Woman dancing
🕺	*Man dancing
🕴️	*Person in suit levitating
👯	Person with bunny ears
👯‍♂️	Man with bunny ears
👯‍♀️	Woman with bunny ears
🧖	*Person in steamy room
🧖‍♂️	*Man in steamy room
🧖‍♀️	*Woman in steamy room
🧗	*Person climbing
🧗‍♂️	*Man climbing
🧗‍♀️	*Woman climbing
🤺	Person fencing
🏇	*Horse racing
⛷️	Skier
🏂	*Snowboarder
🏌️	*Person golfing
🏌️‍♂️	*Man golfing
🏌️‍♀️	*Woman golfing
🏄	*Person surfing
🏄‍♂️	*Man surfing
🏄‍♀️	*Woman surfing
🚣	*Person rowing boat
🚣‍♂️	*Man rowing boat
🚣‍♀️	*Woman rowing boat
🏊	*Person swimming
🏊‍♂️	*Man swimming
🏊‍♀️	*Woman swimming
⛹️	*Person bouncing ball
⛹️‍♂️	*Man bouncing ball
⛹️‍♀️	*Woman bouncing ball
🏋️	*Person lifting weights
🏋️‍♂️	*Man lifting weights
🏋️‍♀️	*Woman lifting weights
🚴	*Person biking
🚴‍♂️	*Man biking
🚴‍♀️	*Woman biking
🚵	*Person mountain biking
🚵‍♂️	*Man mountain biking
🚵‍♀️	*Woman mountain biking
🤸	*Person cartwheeling
🤸‍♂️	*Man cartwheeling
🤸‍♀️	*Woman cartwheeling
🤼	Person wrestling
🤼‍♂️	Man wrestling
🤼‍♀️	Woman wrestling
🤽	*Person playing water polo
🤽‍♂️	*Man playing water polo
🤽‍♀️	*Woman playing water polo
🤾	*Person playing handball
🤾‍♂️	*Man playing handball
🤾‍♀️	*Woman playing handball
🤹	*Person juggling
🤹‍♂️	*Man juggling
🤹‍♀️	*Woman juggling
🧘	*Person in lotus position
🧘‍♂️	*Man in lotus position
🧘‍♀️	*Woman in lotus position
🛀	*Person taking bath
🛌	*Person in bed
🗣️	Speaking head
👤	Bust in silhouette
👥	Busts in silhouette
🫂	People hugging
👣	Footprints"""

    const val NATURE: String = """🐵	Monkey face
🐒	Monkey
🦍	Gorilla
🦧	Orangutan
🐶	Dog face
🐕	Dog
🦮	Guide dog
🐕‍🦺	Service dog
🐩	Poodle
🐺	Wolf
🦊	Fox
🦝	Raccoon
🐱	Cat face
🐈	Cat
🐈‍⬛	Black cat
🦁	Lion
🐯	Tiger face
🐅	Tiger
🐆	Leopard
🐴	Horse face
🫎	Moose
🫏	Donkey
🐎	Horse
🦄	Unicorn
🦓	Zebra
🦌	Deer
🦬	Bison
🐮	Cow face
🐂	Ox
🐃	Water buffalo
🐄	Cow
🐷	Pig face
🐖	Pig
🐗	Boar
🐽	Pig nose
🐏	Ram
🐑	Ewe
🐐	Goat
🐪	Camel
🐫	Two-hump camel
🦙	Llama
🦒	Giraffe
🐘	Elephant
🦣	Mammoth
🦏	Rhinoceros
🦛	Hippopotamus
🐭	Mouse face
🐁	Mouse
🐀	Rat
🐹	Hamster
🐰	Rabbit face
🐇	Rabbit
🐿️	Chipmunk
🦫	Beaver
🦔	Hedgehog
🦇	Bat
🐻	Bear
🐻‍❄️	Polar bear
🐨	Koala
🐼	Panda
🦥	Sloth
🦦	Otter
🦨	Skunk
🦘	Kangaroo
🦡	Badger
🐾	Paw prints
🦃	Turkey
🐔	Chicken
🐓	Rooster
🐣	Hatching chick
🐤	Baby chick
🐥	Front-facing baby chick
🐦	Bird
🐧	Penguin
🕊️	Dove
🦅	Eagle
🦆	Duck
🦢	Swan
🦉	Owl
🦤	Dodo
🪶	Feather
🦩	Flamingo
🦚	Peacock
🦜	Parrot
🪽	Wing
🐦‍⬛	Blackbird
🪿	Goose
🐦‍🔥	Phoenix bird
🐸	Frog
🐊	Crocodile
🐢	Turtle
🦎	Lizard
🐍	Snake
🐲	Dragon face
🐉	Dragon
🦕	Sauropod
🦖	T-rex
🐳	Spouting whale
🐋	Whale
🐬	Dolphin
🦭	Seal
🐟	Fish
🐠	Tropical fish
🐡	Blowfish
🦈	Shark
🐙	Octopus
🐚	Spiral shell
🪸	Coral
🪼	Jellyfish
🦀	Crab
🦞	Lobster
🦐	Shrimp
🦑	Squid
🦪	Oyster
🐌	Snail
🦋	Butterfly
🐛	Bug
🐜	Ant
🐝	Honeybee
🪲	Beetle
🐞	Lady beetle
🦗	Cricket
🪳	Cockroach
🕷️	Spider
🕸️	Spider web
🦂	Scorpion
🦟	Mosquito
🪰	Fly
🪱	Worm
🦠	Microbe
💐	Bouquet
🌸	Cherry blossom
💮	White flower
🪷	Lotus
🏵️	Rosette
🌹	Rose
🥀	Wilted flower
🌺	Hibiscus
🌻	Sunflower
🌼	Blossom
🌷	Tulip
🪻	Hyacinth
🌱	Seedling
🪴	Potted plant
🌲	Evergreen tree
🌳	Deciduous tree
🌴	Palm tree
🌵	Cactus
🌾	Sheaf of rice
🌿	Herb
☘️	Shamrock
🍀	Four leaf clover
🍁	Maple leaf
🍂	Fallen leaf
🍃	Leaf fluttering in wind
🪹	Empty nest
🪺	Nest with eggs
🍄	Mushroom"""

    const val FOOD: String = """🍇	Grapes
🍈	Melon
🍉	Watermelon
🍊	Tangerine
🍋	Lemon
🍋‍🟩	Lime
🍌	Banana
🍍	Pineapple
🥭	Mango
🍎	Red apple
🍏	Green apple
🍐	Pear
🍑	Peach
🍒	Cherries
🍓	Strawberry
🫐	Blueberries
🥝	Kiwi fruit
🍅	Tomato
🫒	Olive
🥥	Coconut
🥑	Avocado
🍆	Eggplant
🥔	Potato
🥕	Carrot
🌽	Ear of corn
🌶️	Hot pepper
🫑	Bell pepper
🥒	Cucumber
🥬	Leafy green
🥦	Broccoli
🧄	Garlic
🧅	Onion
🥜	Peanuts
🫘	Beans
🌰	Chestnut
🫚	Ginger root
🫛	Pea pod
🍄‍🟫	Brown mushroom
🍞	Bread
🥐	Croissant
🥖	Baguette bread
🫓	Flatbread
🥨	Pretzel
🥯	Bagel
🥞	Pancakes
🧇	Waffle
🧀	Cheese wedge
🍖	Meat on bone
🍗	Poultry leg
🥩	Cut of meat
🥓	Bacon
🍔	Hamburger
🍟	French fries
🍕	Pizza
🌭	Hot dog
🥪	Sandwich
🌮	Taco
🌯	Burrito
🫔	Tamale
🥙	Stuffed flatbread
🧆	Falafel
🥚	Egg
🍳	Cooking
🥘	Shallow pan of food
🍲	Pot of food
🫕	Fondue
🥣	Bowl with spoon
🥗	Green salad
🍿	Popcorn
🧈	Butter
🧂	Salt
🥫	Canned food
🍱	Bento box
🍘	Rice cracker
🍙	Rice ball
🍚	Cooked rice
🍛	Curry rice
🍜	Steaming bowl
🍝	Spaghetti
🍠	Roasted sweet potato
🍢	Oden
🍣	Sushi
🍤	Fried shrimp
🍥	Fish cake with swirl
🥮	Moon cake
🍡	Dango
🥟	Dumpling
🥠	Fortune cookie
🥡	Takeout box
🍦	Soft ice cream
🍧	Shaved ice
🍨	Ice cream
🍩	Doughnut
🍪	Cookie
🎂	Birthday cake
🍰	Shortcake
🧁	Cupcake
🥧	Pie
🍫	Chocolate bar
🍬	Candy
🍭	Lollipop
🍮	Custard
🍯	Honey pot
🍼	Baby bottle
🥛	Glass of milk
☕	Hot beverage
🫖	Teapot
🍵	Teacup without handle
🍶	Sake
🍾	Bottle with popping cork
🍷	Wine glass
🍸	Cocktail glass
🍹	Tropical drink
🍺	Beer mug
🍻	Clinking beer mugs
🥂	Clinking glasses
🥃	Tumbler glass
🫗	Pouring liquid
🥤	Cup with straw
🧋	Bubble tea
🧃	Beverage box
🧉	Mate
🧊	Ice
🥢	Chopsticks
🍽️	Fork and knife with plate
🍴	Fork and knife
🥄	Spoon
🔪	Kitchen knife
🫙	Jar
🏺	Amphora"""

    const val ACTIVITIES: String = """🎃	Jack-o-lantern
🎄	Christmas tree
🎆	Fireworks
🎇	Sparkler
🧨	Firecracker
✨	Sparkles
🎈	Balloon
🎉	Party popper
🎊	Confetti ball
🎋	Tanabata tree
🎍	Pine decoration
🎎	Japanese dolls
🎏	Carp streamer
🎐	Wind chime
🎑	Moon viewing ceremony
🧧	Red envelope
🎀	Ribbon
🎁	Wrapped gift
🎗️	Reminder ribbon
🎟️	Admission tickets
🎫	Ticket
🎖️	Military medal
🏆	Trophy
🏅	Sports medal
🥇	1st place medal
🥈	2nd place medal
🥉	3rd place medal
⚽	Soccer ball
⚾	Baseball
🥎	Softball
🏀	Basketball
🏐	Volleyball
🏈	American football
🏉	Rugby football
🎾	Tennis
🥏	Flying disc
🎳	Bowling
🏏	Cricket game
🏑	Field hockey
🏒	Ice hockey
🥍	Lacrosse
🏓	Ping pong
🏸	Badminton
🥊	Boxing glove
🥋	Martial arts uniform
🥅	Goal net
⛳	Flag in hole
⛸️	Ice skate
🎣	Fishing pole
🤿	Diving mask
🎽	Running shirt
🎿	Skis
🛷	Sled
🥌	Curling stone
🎯	Bullseye
🪀	Yo-yo
🪁	Kite
🔫	Water pistol
🎱	Pool 8 ball
🔮	Crystal ball
🪄	Magic wand
🎮	Video game
🕹️	Joystick
🎰	Slot machine
🎲	Game die
🧩	Puzzle piece
🧸	Teddy bear
🪅	Piñata
🪩	Mirror ball
🪆	Nesting dolls
♠️	Spade suit
♥️	Heart suit
♦️	Diamond suit
♣️	Club suit
♟️	Chess pawn
🃏	Joker
🀄	Mahjong red dragon
🎴	Flower playing cards
🎭	Performing arts
🖼️	Framed picture
🎨	Artist palette
🧵	Thread
🪡	Sewing needle
🧶	Yarn
🪢	Knot"""

    const val TRAVEL: String = """🌍	Globe showing europe-africa
🌎	Globe showing americas
🌏	Globe showing asia-australia
🌐	Globe with meridians
🗺️	World map
🗾	Map of japan
🧭	Compass
🏔️	Snow-capped mountain
⛰️	Mountain
🌋	Volcano
🗻	Mount fuji
🏕️	Camping
🏖️	Beach with umbrella
🏜️	Desert
🏝️	Desert island
🏞️	National park
🏟️	Stadium
🏛️	Classical building
🏗️	Building construction
🧱	Brick
🪨	Rock
🪵	Wood
🛖	Hut
🏘️	Houses
🏚️	Derelict house
🏠	House
🏡	House with garden
🏢	Office building
🏣	Japanese post office
🏤	Post office
🏥	Hospital
🏦	Bank
🏨	Hotel
🏩	Love hotel
🏪	Convenience store
🏫	School
🏬	Department store
🏭	Factory
🏯	Japanese castle
🏰	Castle
💒	Wedding
🗼	Tokyo tower
🗽	Statue of liberty
⛪	Church
🕌	Mosque
🛕	Hindu temple
🕍	Synagogue
⛩️	Shinto shrine
🕋	Kaaba
⛲	Fountain
⛺	Tent
🌁	Foggy
🌃	Night with stars
🏙️	Cityscape
🌄	Sunrise over mountains
🌅	Sunrise
🌆	Cityscape at dusk
🌇	Sunset
🌉	Bridge at night
♨️	Hot springs
🎠	Carousel horse
🛝	Playground slide
🎡	Ferris wheel
🎢	Roller coaster
💈	Barber pole
🎪	Circus tent
🚂	Locomotive
🚃	Railway car
🚄	High-speed train
🚅	Bullet train
🚆	Train
🚇	Metro
🚈	Light rail
🚉	Station
🚊	Tram
🚝	Monorail
🚞	Mountain railway
🚋	Tram car
🚌	Bus
🚍	Oncoming bus
🚎	Trolleybus
🚐	Minibus
🚑	Ambulance
🚒	Fire engine
🚓	Police car
🚔	Oncoming police car
🚕	Taxi
🚖	Oncoming taxi
🚗	Automobile
🚘	Oncoming automobile
🚙	Sport utility vehicle
🛻	Pickup truck
🚚	Delivery truck
🚛	Articulated lorry
🚜	Tractor
🏎️	Racing car
🏍️	Motorcycle
🛵	Motor scooter
🦽	Manual wheelchair
🦼	Motorized wheelchair
🛺	Auto rickshaw
🚲	Bicycle
🛴	Kick scooter
🛹	Skateboard
🛼	Roller skate
🚏	Bus stop
🛣️	Motorway
🛤️	Railway track
🛢️	Oil drum
⛽	Fuel pump
🛞	Wheel
🚨	Police car light
🚥	Horizontal traffic light
🚦	Vertical traffic light
🛑	Stop sign
🚧	Construction
⚓	Anchor
🛟	Ring buoy
⛵	Sailboat
🛶	Canoe
🚤	Speedboat
🛳️	Passenger ship
⛴️	Ferry
🛥️	Motor boat
🚢	Ship
✈️	Airplane
🛩️	Small airplane
🛫	Airplane departure
🛬	Airplane arrival
🪂	Parachute
💺	Seat
🚁	Helicopter
🚟	Suspension railway
🚠	Mountain cableway
🚡	Aerial tramway
🛰️	Satellite
🚀	Rocket
🛸	Flying saucer
🛎️	Bellhop bell
🧳	Luggage
⌛	Hourglass done
⏳	Hourglass not done
⌚	Watch
⏰	Alarm clock
⏱️	Stopwatch
⏲️	Timer clock
🕰️	Mantelpiece clock
🕛	Twelve oclock
🕧	Twelve-thirty
🕐	One oclock
🕜	One-thirty
🕑	Two oclock
🕝	Two-thirty
🕒	Three oclock
🕞	Three-thirty
🕓	Four oclock
🕟	Four-thirty
🕔	Five oclock
🕠	Five-thirty
🕕	Six oclock
🕡	Six-thirty
🕖	Seven oclock
🕢	Seven-thirty
🕗	Eight oclock
🕣	Eight-thirty
🕘	Nine oclock
🕤	Nine-thirty
🕙	Ten oclock
🕥	Ten-thirty
🕚	Eleven oclock
🕦	Eleven-thirty
🌑	New moon
🌒	Waxing crescent moon
🌓	First quarter moon
🌔	Waxing gibbous moon
🌕	Full moon
🌖	Waning gibbous moon
🌗	Last quarter moon
🌘	Waning crescent moon
🌙	Crescent moon
🌚	New moon face
🌛	First quarter moon face
🌜	Last quarter moon face
🌡️	Thermometer
☀️	Sun
🌝	Full moon face
🌞	Sun with face
🪐	Ringed planet
⭐	Star
🌟	Glowing star
🌠	Shooting star
🌌	Milky way
☁️	Cloud
⛅	Sun behind cloud
⛈️	Cloud with lightning and rain
🌤️	Sun behind small cloud
🌥️	Sun behind large cloud
🌦️	Sun behind rain cloud
🌧️	Cloud with rain
🌨️	Cloud with snow
🌩️	Cloud with lightning
🌪️	Tornado
🌫️	Fog
🌬️	Wind face
🌀	Cyclone
🌈	Rainbow
🌂	Closed umbrella
☂️	Umbrella
☔	Umbrella with rain drops
⛱️	Umbrella on ground
⚡	High voltage
❄️	Snowflake
☃️	Snowman
⛄	Snowman without snow
☄️	Comet
🔥	Fire
💧	Droplet
🌊	Water wave"""

    const val OBJECTS: String = """👓	Glasses
🕶️	Sunglasses
🥽	Goggles
🥼	Lab coat
🦺	Safety vest
👔	Necktie
👕	T-shirt
👖	Jeans
🧣	Scarf
🧤	Gloves
🧥	Coat
🧦	Socks
👗	Dress
👘	Kimono
🥻	Sari
🩱	One-piece swimsuit
🩲	Briefs
🩳	Shorts
👙	Bikini
👚	Womans clothes
🪭	Folding hand fan
👛	Purse
👜	Handbag
👝	Clutch bag
🛍️	Shopping bags
🎒	Backpack
🩴	Thong sandal
👞	Mans shoe
👟	Running shoe
🥾	Hiking boot
🥿	Flat shoe
👠	High-heeled shoe
👡	Womans sandal
🩰	Ballet shoes
👢	Womans boot
🪮	Hair pick
👑	Crown
👒	Womans hat
🎩	Top hat
🎓	Graduation cap
🧢	Billed cap
🪖	Military helmet
⛑️	Rescue workers helmet
📿	Prayer beads
💄	Lipstick
💍	Ring
💎	Gem stone
🔇	Muted speaker
🔈	Speaker low volume
🔉	Speaker medium volume
🔊	Speaker high volume
📢	Loudspeaker
📣	Megaphone
📯	Postal horn
🔔	Bell
🔕	Bell with slash
🎼	Musical score
🎵	Musical note
🎶	Musical notes
🎙️	Studio microphone
🎚️	Level slider
🎛️	Control knobs
🎤	Microphone
🎧	Headphone
📻	Radio
🎷	Saxophone
🎺	Trumpet
🪗	Accordion
🎸	Guitar
🎹	Musical keyboard
🎻	Violin
🪕	Banjo
🥁	Drum
🪘	Long drum
🪇	Maracas
🪈	Flute
📱	Mobile phone
📲	Mobile phone with arrow
☎️	Telephone
📞	Telephone receiver
📟	Pager
📠	Fax machine
🔋	Battery
🪫	Low battery
🔌	Electric plug
💻	Laptop
🖥️	Desktop computer
🖨️	Printer
⌨️	Keyboard
🖱️	Computer mouse
🖲️	Trackball
💽	Computer disk
💾	Floppy disk
💿	Optical disk
📀	Dvd
🧮	Abacus
🎥	Movie camera
🎞️	Film frames
📽️	Film projector
🎬	Clapper board
📺	Television
📷	Camera
📸	Camera with flash
📹	Video camera
📼	Videocassette
🔍	Magnifying glass tilted left
🔎	Magnifying glass tilted right
🕯️	Candle
💡	Light bulb
🔦	Flashlight
🏮	Red paper lantern
🪔	Diya lamp
📔	Notebook with decorative cover
📕	Closed book
📖	Open book
📗	Green book
📘	Blue book
📙	Orange book
📚	Books
📓	Notebook
📒	Ledger
📃	Page with curl
📜	Scroll
📄	Page facing up
📰	Newspaper
🗞️	Rolled-up newspaper
📑	Bookmark tabs
🔖	Bookmark
🏷️	Label
🪙	Coin
💰	Money bag
💴	Yen banknote
💵	Dollar banknote
💶	Euro banknote
💷	Pound banknote
💸	Money with wings
💳	Credit card
🧾	Receipt
💹	Chart increasing with yen
✉️	Envelope
📧	E-mail
📨	Incoming envelope
📩	Envelope with arrow
📤	Outbox tray
📥	Inbox tray
📦	Package
📫	Closed mailbox with raised flag
📪	Closed mailbox with lowered flag
📬	Open mailbox with raised flag
📭	Open mailbox with lowered flag
📮	Postbox
🗳️	Ballot box with ballot
✏️	Pencil
✒️	Black nib
🖋️	Fountain pen
🖊️	Pen
🖌️	Paintbrush
🖍️	Crayon
📝	Memo
💼	Briefcase
📁	File folder
📂	Open file folder
🗂️	Card index dividers
📅	Calendar
📆	Tear-off calendar
🗒️	Spiral notepad
🗓️	Spiral calendar
📇	Card index
📈	Chart increasing
📉	Chart decreasing
📊	Bar chart
📋	Clipboard
📌	Pushpin
📍	Round pushpin
📎	Paperclip
🖇️	Linked paperclips
📏	Straight ruler
📐	Triangular ruler
✂️	Scissors
🗃️	Card file box
🗄️	File cabinet
🗑️	Wastebasket
🔒	Locked
🔓	Unlocked
🔏	Locked with pen
🔐	Locked with key
🔑	Key
🗝️	Old key
🔨	Hammer
🪓	Axe
⛏️	Pick
⚒️	Hammer and pick
🛠️	Hammer and wrench
🗡️	Dagger
⚔️	Crossed swords
💣	Bomb
🪃	Boomerang
🏹	Bow and arrow
🛡️	Shield
🪚	Carpentry saw
🔧	Wrench
🪛	Screwdriver
🔩	Nut and bolt
⚙️	Gear
🗜️	Clamp
⚖️	Balance scale
🦯	White cane
🔗	Link
⛓️‍💥	Broken chain
⛓️	Chains
🪝	Hook
🧰	Toolbox
🧲	Magnet
🪜	Ladder
⚗️	Alembic
🧪	Test tube
🧫	Petri dish
🧬	Dna
🔬	Microscope
🔭	Telescope
📡	Satellite antenna
💉	Syringe
🩸	Drop of blood
💊	Pill
🩹	Adhesive bandage
🩼	Crutch
🩺	Stethoscope
🩻	X-ray
🚪	Door
🛗	Elevator
🪞	Mirror
🪟	Window
🛏️	Bed
🛋️	Couch and lamp
🪑	Chair
🚽	Toilet
🪠	Plunger
🚿	Shower
🛁	Bathtub
🪤	Mouse trap
🪒	Razor
🧴	Lotion bottle
🧷	Safety pin
🧹	Broom
🧺	Basket
🧻	Roll of paper
🪣	Bucket
🧼	Soap
🫧	Bubbles
🪥	Toothbrush
🧽	Sponge
🧯	Fire extinguisher
🛒	Shopping cart
🚬	Cigarette
⚰️	Coffin
🪦	Headstone
⚱️	Funeral urn
🧿	Nazar amulet
🪬	Hamsa
🗿	Moai
🪧	Placard
🪪	Identification card"""

    const val SYMBOLS: String = """🏧	Atm sign
🚮	Litter in bin sign
🚰	Potable water
♿	Wheelchair symbol
🚹	Mens room
🚺	Womens room
🚻	Restroom
🚼	Baby symbol
🚾	Water closet
🛂	Passport control
🛃	Customs
🛄	Baggage claim
🛅	Left luggage
⚠️	Warning
🚸	Children crossing
⛔	No entry
🚫	Prohibited
🚳	No bicycles
🚭	No smoking
🚯	No littering
🚱	Non-potable water
🚷	No pedestrians
📵	No mobile phones
🔞	No one under eighteen
☢️	Radioactive
☣️	Biohazard
⬆️	Up arrow
↗️	Up-right arrow
➡️	Right arrow
↘️	Down-right arrow
⬇️	Down arrow
↙️	Down-left arrow
⬅️	Left arrow
↖️	Up-left arrow
↕️	Up-down arrow
↔️	Left-right arrow
↩️	Right arrow curving left
↪️	Left arrow curving right
⤴️	Right arrow curving up
⤵️	Right arrow curving down
🔃	Clockwise vertical arrows
🔄	Counterclockwise arrows button
🔙	Back arrow
🔚	End arrow
🔛	On! arrow
🔜	Soon arrow
🔝	Top arrow
🛐	Place of worship
⚛️	Atom symbol
🕉️	Om
✡️	Star of david
☸️	Wheel of dharma
☯️	Yin yang
✝️	Latin cross
☦️	Orthodox cross
☪️	Star and crescent
☮️	Peace symbol
🕎	Menorah
🔯	Dotted six-pointed star
🪯	Khanda
♈	Aries
♉	Taurus
♊	Gemini
♋	Cancer
♌	Leo
♍	Virgo
♎	Libra
♏	Scorpio
♐	Sagittarius
♑	Capricorn
♒	Aquarius
♓	Pisces
⛎	Ophiuchus
🔀	Shuffle tracks button
🔁	Repeat button
🔂	Repeat single button
▶️	Play button
⏩	Fast-forward button
⏭️	Next track button
⏯️	Play or pause button
◀️	Reverse button
⏪	Fast reverse button
⏮️	Last track button
🔼	Upwards button
⏫	Fast up button
🔽	Downwards button
⏬	Fast down button
⏸️	Pause button
⏹️	Stop button
⏺️	Record button
⏏️	Eject button
🎦	Cinema
🔅	Dim button
🔆	Bright button
📶	Antenna bars
🛜	Wireless
📳	Vibration mode
📴	Mobile phone off
♀️	Female sign
♂️	Male sign
⚧️	Transgender symbol
✖️	Multiply
➕	Plus
➖	Minus
➗	Divide
🟰	Heavy equals sign
♾️	Infinity
‼️	Double exclamation mark
⁉️	Exclamation question mark
❓	Red question mark
❔	White question mark
❕	White exclamation mark
❗	Red exclamation mark
〰️	Wavy dash
💱	Currency exchange
💲	Heavy dollar sign
⚕️	Medical symbol
♻️	Recycling symbol
⚜️	Fleur-de-lis
🔱	Trident emblem
📛	Name badge
🔰	Japanese symbol for beginner
⭕	Hollow red circle
✅	Check mark button
☑️	Check box with check
✔️	Check mark
❌	Cross mark
❎	Cross mark button
➰	Curly loop
➿	Double curly loop
〽️	Part alternation mark
✳️	Eight-spoked asterisk
✴️	Eight-pointed star
❇️	Sparkle
©️	Copyright
®️	Registered
™️	Trade mark
#️⃣	Keycap hashtag
*️⃣	Keycap asterisk
0️⃣	Keycap 0
1️⃣	Keycap 1
2️⃣	Keycap 2
3️⃣	Keycap 3
4️⃣	Keycap 4
5️⃣	Keycap 5
6️⃣	Keycap 6
7️⃣	Keycap 7
8️⃣	Keycap 8
9️⃣	Keycap 9
🔟	Keycap 10
🔠	Input latin uppercase
🔡	Input latin lowercase
🔢	Input numbers
🔣	Input symbols
🔤	Input latin letters
🅰️	A button blood type
🆎	Ab button blood type
🅱️	B button blood type
🆑	Cl button
🆒	Cool button
🆓	Free button
ℹ️	Information
🆔	Id button
Ⓜ️	Circled m
🆕	New button
🆖	Ng button
🆗	Ok button
🅿️	P button
🆘	Sos button
🆙	Up! button
🆚	Vs button
🈁	Japanese here button
🈂️	Japanese service charge button
🈷️	Japanese monthly amount button
🈶	Japanese not free of charge button
🈯	Japanese reserved button
🉐	Japanese bargain button
🈹	Japanese discount button
🈚	Japanese free of charge button
🈲	Japanese prohibited button
🉑	Japanese acceptable button
🈸	Japanese application button
🈴	Japanese passing grade button
🈳	Japanese vacancy button
㊗️	Japanese congratulations button
㊙️	Japanese secret button
🈺	Japanese open for business button
🈵	Japanese no vacancy button
🔴	Red circle
🟠	Orange circle
🟡	Yellow circle
🟢	Green circle
🔵	Blue circle
🟣	Purple circle
🟤	Brown circle
⚫	Black circle
⚪	White circle
🟥	Red square
🟧	Orange square
🟨	Yellow square
🟩	Green square
🟦	Blue square
🟪	Purple square
🟫	Brown square
⬛	Black large square
⬜	White large square
◼️	Black medium square
◻️	White medium square
◾	Black medium-small square
◽	White medium-small square
▪️	Black small square
▫️	White small square
🔶	Large orange diamond
🔷	Large blue diamond
🔸	Small orange diamond
🔹	Small blue diamond
🔺	Red triangle
🔻	Red triangle pointed down
💠	Diamond with a dot
🔘	Radio button
🔳	White square button
🔲	Black square button"""
}
