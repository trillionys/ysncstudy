import random
import streamlit as st

st.set_page_config(page_title="Daily 100 Phrases", page_icon="🌍", layout="wide")

LANGS = {
    "ko": {"name": "한국어", "flag": "🇰🇷", "dir": "ltr"},
    "en": {"name": "English", "flag": "🇺🇸", "dir": "ltr"},
    "ja": {"name": "日本語", "flag": "🇯🇵", "dir": "ltr"},
    "zh": {"name": "中文", "flag": "🇨🇳", "dir": "ltr"},
    "es": {"name": "Español", "flag": "🇪🇸", "dir": "ltr"},
    "ar": {"name": "العربية", "flag": "🇸🇦", "dir": "rtl"},
    "hi": {"name": "हिन्दी", "flag": "🇮🇳", "dir": "ltr"},
    "de": {"name": "Deutsch", "flag": "🇩🇪", "dir": "ltr"},
    "fr": {"name": "Français", "flag": "🇫🇷", "dir": "ltr"},
    "it": {"name": "Italiano", "flag": "🇮🇹", "dir": "ltr"},
}

CATEGORIES = [
    "인사 & 기본 표현",
    "감사 & 예의",
    "자주 하는 질문",
    "상태 & 감정",
    "동의 & 반응",
    "인간관계",
    "쇼핑 & 음식",
    "이동 & 여행",
    "전화 & 온라인",
    "감탄 & 이해",
]

PHRASES = [
    # 1. Greetings
    {"cat":"인사 & 기본 표현","ko":"안녕하세요.","en":"Hello.","ja":"こんにちは。","zh":"你好。","es":"Hola.","ar":"مرحبًا.","hi":"नमस्ते।","de":"Hallo.","fr":"Bonjour.","it":"Ciao."},
    {"cat":"인사 & 기본 표현","ko":"안녕.","en":"Hi.","ja":"やあ。","zh":"嗨。","es":"Hola.","ar":"أهلًا.","hi":"हाय।","de":"Hi.","fr":"Salut.","it":"Ciao."},
    {"cat":"인사 & 기본 표현","ko":"좋은 아침이에요.","en":"Good morning.","ja":"おはようございます。","zh":"早上好。","es":"Buenos días.","ar":"صباح الخير.","hi":"सुप्रभात।","de":"Guten Morgen.","fr":"Bonjour.","it":"Buongiorno."},
    {"cat":"인사 & 기본 표현","ko":"잘 자요.","en":"Good night.","ja":"おやすみなさい。","zh":"晚安。","es":"Buenas noches.","ar":"تصبح على خير.","hi":"शुभ रात्रि।","de":"Gute Nacht.","fr":"Bonne nuit.","it":"Buona notte."},
    {"cat":"인사 & 기본 표현","ko":"어떻게 지내요?","en":"How are you?","ja":"お元気ですか？","zh":"你好吗？","es":"¿Cómo estás?","ar":"كيف حالك؟","hi":"आप कैसे हैं?","de":"Wie geht es dir?","fr":"Comment ça va ?","it":"Come stai?"},
    {"cat":"인사 & 기본 표현","ko":"저는 괜찮아요.","en":"I’m fine.","ja":"元気です。","zh":"我很好。","es":"Estoy bien.","ar":"أنا بخير.","hi":"मैं ठीक हूँ।","de":"Mir geht es gut.","fr":"Je vais bien.","it":"Sto bene."},
    {"cat":"인사 & 기본 표현","ko":"만나서 반가워요.","en":"Nice to meet you.","ja":"はじめまして。","zh":"很高兴认识你。","es":"Encantado de conocerte.","ar":"تشرفت بمعرفتك.","hi":"आपसे मिलकर अच्छा लगा।","de":"Schön, dich kennenzulernen.","fr":"Ravi de vous rencontrer.","it":"Piacere di conoscerti."},
    {"cat":"인사 & 기본 표현","ko":"나중에 봐요.","en":"See you later.","ja":"また後で。","zh":"回头见。","es":"Hasta luego.","ar":"أراك لاحقًا.","hi":"बाद में मिलते हैं।","de":"Bis später.","fr":"À plus tard.","it":"A dopo."},
    {"cat":"인사 & 기본 표현","ko":"몸조심하세요.","en":"Take care.","ja":"気をつけてね。","zh":"保重。","es":"Cuídate.","ar":"اعتنِ بنفسك.","hi":"अपना ख्याल रखना।","de":"Pass auf dich auf.","fr":"Prends soin de toi.","it":"Abbi cura di te."},
    {"cat":"인사 & 기본 표현","ko":"좋은 하루 보내세요.","en":"Have a nice day.","ja":"良い一日を。","zh":"祝你今天愉快。","es":"Que tengas un buen día.","ar":"أتمنى لك يومًا سعيدًا.","hi":"आपका दिन शुभ हो।","de":"Hab einen schönen Tag.","fr":"Bonne journée.","it":"Buona giornata."},

    # 2. Thanks
    {"cat":"감사 & 예의","ko":"감사합니다.","en":"Thank you.","ja":"ありがとうございます。","zh":"谢谢。","es":"Gracias.","ar":"شكرًا.","hi":"धन्यवाद।","de":"Danke.","fr":"Merci.","it":"Grazie."},
    {"cat":"감사 & 예의","ko":"정말 고마워요.","en":"Thanks a lot.","ja":"本当にありがとう。","zh":"非常感谢。","es":"Muchas gracias.","ar":"شكرًا جزيلًا.","hi":"बहुत धन्यवाद।","de":"Vielen Dank.","fr":"Merci beaucoup.","it":"Grazie mille."},
    {"cat":"감사 & 예의","ko":"천만에요.","en":"You’re welcome.","ja":"どういたしまして。","zh":"不客气。","es":"De nada.","ar":"على الرحب والسعة.","hi":"आपका स्वागत है।","de":"Gern geschehen.","fr":"De rien.","it":"Prego."},
    {"cat":"감사 & 예의","ko":"실례합니다.","en":"Excuse me.","ja":"すみません。","zh":"不好意思。","es":"Disculpa.","ar":"عذرًا.","hi":"माफ़ कीजिए।","de":"Entschuldigung.","fr":"Excusez-moi.","it":"Mi scusi."},
    {"cat":"감사 & 예의","ko":"미안해요.","en":"I’m sorry.","ja":"ごめんなさい。","zh":"对不起。","es":"Lo siento.","ar":"أنا آسف.","hi":"मुझे माफ़ कीजिए।","de":"Es tut mir leid.","fr":"Je suis désolé.","it":"Mi dispiace."},
    {"cat":"감사 & 예의","ko":"괜찮아요.","en":"No problem.","ja":"問題ないよ。","zh":"没问题。","es":"No hay problema.","ar":"لا مشكلة.","hi":"कोई बात नहीं।","de":"Kein Problem.","fr":"Pas de problème.","it":"Nessun problema."},
    {"cat":"감사 & 예의","ko":"괜찮습니다.","en":"That’s okay.","ja":"大丈夫です。","zh":"没关系。","es":"Está bien.","ar":"لا بأس.","hi":"ठीक है।","de":"Das ist okay.","fr":"Ce n’est pas grave.","it":"Va bene."},
    {"cat":"감사 & 예의","ko":"부탁해요.","en":"Please.","ja":"お願いします。","zh":"请。","es":"Por favor.","ar":"من فضلك.","hi":"कृपया।","de":"Bitte.","fr":"S’il vous plaît.","it":"Per favore."},
    {"cat":"감사 & 예의","ko":"먼저 가세요.","en":"After you.","ja":"お先にどうぞ。","zh":"你先请。","es":"Después de ti.","ar":"تفضل أنت أولًا.","hi":"आप पहले।","de":"Nach dir.","fr":"Après vous.","it":"Prego, prima tu."},
    {"cat":"감사 & 예의","ko":"정말 감사해요.","en":"I appreciate it.","ja":"感謝しています。","zh":"我很感激。","es":"Lo agradezco.","ar":"أقدّر ذلك.","hi":"मैं इसकी सराहना करता हूँ।","de":"Ich weiß das zu schätzen.","fr":"J’apprécie beaucoup.","it":"Lo apprezzo."},

    # 3 Questions
    {"cat":"자주 하는 질문","ko":"이름이 뭐예요?","en":"What’s your name?","ja":"お名前は何ですか？","zh":"你叫什么名字？","es":"¿Cómo te llamas?","ar":"ما اسمك؟","hi":"आपका नाम क्या है?","de":"Wie heißt du?","fr":"Comment vous appelez-vous ?","it":"Come ti chiami?"},
    {"cat":"자주 하는 질문","ko":"어디에서 왔어요?","en":"Where are you from?","ja":"どちらの出身ですか？","zh":"你来自哪里？","es":"¿De dónde eres?","ar":"من أين أنت؟","hi":"आप कहाँ से हैं?","de":"Woher kommst du?","fr":"D’où venez-vous ?","it":"Di dove sei?"},
    {"cat":"자주 하는 질문","ko":"몇 살이에요?","en":"How old are you?","ja":"何歳ですか？","zh":"你多大了？","es":"¿Cuántos años tienes?","ar":"كم عمرك؟","hi":"आपकी उम्र कितनी है?","de":"Wie alt bist du?","fr":"Quel âge avez-vous ?","it":"Quanti anni hai?"},
    {"cat":"자주 하는 질문","ko":"무슨 일 하세요?","en":"What do you do?","ja":"お仕事は何ですか？","zh":"你是做什么工作的？","es":"¿A qué te dedicas?","ar":"ماذا تعمل؟","hi":"आप क्या काम करते हैं?","de":"Was machst du beruflich?","fr":"Que faites-vous dans la vie ?","it":"Che lavoro fai?"},
    {"cat":"자주 하는 질문","ko":"지금 몇 시예요?","en":"What time is it?","ja":"今何時ですか？","zh":"现在几点了？","es":"¿Qué hora es?","ar":"كم الساعة؟","hi":"कितने बजे हैं?","de":"Wie spät ist es?","fr":"Quelle heure est-il ?","it":"Che ore sono?"},
    {"cat":"자주 하는 질문","ko":"화장실이 어디예요?","en":"Where is the bathroom?","ja":"トイレはどこですか？","zh":"洗手间在哪里？","es":"¿Dónde está el baño?","ar":"أين الحمّام؟","hi":"बाथरूम कहाँ है?","de":"Wo ist die Toilette?","fr":"Où sont les toilettes ?","it":"Dov’è il bagno?"},
    {"cat":"자주 하는 질문","ko":"이거 얼마예요?","en":"How much is this?","ja":"これはいくらですか？","zh":"这个多少钱？","es":"¿Cuánto cuesta esto?","ar":"كم سعر هذا؟","hi":"यह कितने का है?","de":"Wie viel kostet das?","fr":"Combien ça coûte ?","it":"Quanto costa questo?"},
    {"cat":"자주 하는 질문","ko":"도와주실 수 있나요?","en":"Can you help me?","ja":"手伝ってくれますか？","zh":"你能帮我吗？","es":"¿Puedes ayudarme?","ar":"هل يمكنك مساعدتي؟","hi":"क्या आप मेरी मदद कर सकते हैं?","de":"Kannst du mir helfen?","fr":"Pouvez-vous m’aider ?","it":"Puoi aiutarmi?"},
    {"cat":"자주 하는 질문","ko":"영어 할 줄 아세요?","en":"Do you speak English?","ja":"英語を話せますか？","zh":"你会说英语吗？","es":"¿Hablas inglés?","ar":"هل تتحدث الإنجليزية؟","hi":"क्या आप अंग्रेज़ी बोलते हैं?","de":"Sprichst du Englisch?","fr":"Parlez-vous anglais ?","it":"Parli inglese?"},
    {"cat":"자주 하는 질문","ko":"무슨 일이 있었어요?","en":"What happened?","ja":"何が起きたの？","zh":"发生了什么？","es":"¿Qué pasó?","ar":"ماذا حدث؟","hi":"क्या हुआ?","de":"Was ist passiert?","fr":"Que s’est-il passé ?","it":"Che cosa è successo?"},

    # 4 Feelings
    {"cat":"상태 & 감정","ko":"배고파요.","en":"I’m hungry.","ja":"お腹がすきました。","zh":"我饿了。","es":"Tengo hambre.","ar":"أنا جائع.","hi":"मुझे भूख लगी है।","de":"Ich habe Hunger.","fr":"J’ai faim.","it":"Ho fame."},
    {"cat":"상태 & 감정","ko":"피곤해요.","en":"I’m tired.","ja":"疲れました。","zh":"我累了。","es":"Estoy cansado.","ar":"أنا متعب.","hi":"मैं थक गया हूँ।","de":"Ich bin müde.","fr":"Je suis fatigué.","it":"Sono stanco."},
    {"cat":"상태 & 감정","ko":"바빠요.","en":"I’m busy.","ja":"忙しいです。","zh":"我很忙。","es":"Estoy ocupado.","ar":"أنا مشغول.","hi":"मैं व्यस्त हूँ।","de":"Ich bin beschäftigt.","fr":"Je suis occupé.","it":"Sono occupato."},
    {"cat":"상태 & 감정","ko":"심심해요.","en":"I’m bored.","ja":"退屈です。","zh":"我很无聊。","es":"Estoy aburrido.","ar":"أنا أشعر بالملل.","hi":"मैं ऊब गया हूँ।","de":"Mir ist langweilig.","fr":"Je m’ennuie.","it":"Mi annoio."},
    {"cat":"상태 & 감정","ko":"행복해요.","en":"I’m happy.","ja":"幸せです。","zh":"我很开心。","es":"Estoy feliz.","ar":"أنا سعيد.","hi":"मैं खुश हूँ।","de":"Ich bin glücklich.","fr":"Je suis heureux.","it":"Sono felice."},
    {"cat":"상태 & 감정","ko":"슬퍼요.","en":"I’m sad.","ja":"悲しいです。","zh":"我很难过。","es":"Estoy triste.","ar":"أنا حزين.","hi":"मैं उदास हूँ।","de":"Ich bin traurig.","fr":"Je suis triste.","it":"Sono triste."},
    {"cat":"상태 & 감정","ko":"몸이 안 좋아요.","en":"I’m sick.","ja":"具合が悪いです。","zh":"我生病了。","es":"Estoy enfermo.","ar":"أنا مريض.","hi":"मैं बीमार हूँ।","de":"Ich bin krank.","fr":"Je suis malade.","it":"Sto male."},
    {"cat":"상태 & 감정","ko":"추워요.","en":"I’m cold.","ja":"寒いです。","zh":"我冷。","es":"Tengo frío.","ar":"أشعر بالبرد.","hi":"मुझे ठंड लग रही है।","de":"Mir ist kalt.","fr":"J’ai froid.","it":"Ho freddo."},
    {"cat":"상태 & 감정","ko":"더워요.","en":"I’m hot.","ja":"暑いです。","zh":"我热。","es":"Tengo calor.","ar":"أشعر بالحر.","hi":"मुझे गर्मी लग रही है।","de":"Mir ist heiß.","fr":"J’ai chaud.","it":"Ho caldo."},
    {"cat":"상태 & 감정","ko":"좀 쉬어야 해요.","en":"I need a break.","ja":"休憩が必要です。","zh":"我需要休息一下。","es":"Necesito un descanso.","ar":"أحتاج إلى استراحة.","hi":"मुझे ब्रेक चाहिए।","de":"Ich brauche eine Pause.","fr":"J’ai besoin d’une pause.","it":"Ho bisogno di una pausa."},

    # 5 Responses
    {"cat":"동의 & 반응","ko":"네.","en":"Yes.","ja":"はい。","zh":"是的。","es":"Sí.","ar":"نعم.","hi":"हाँ।","de":"Ja.","fr":"Oui.","it":"Sì."},
    {"cat":"동의 & 반응","ko":"아니요.","en":"No.","ja":"いいえ。","zh":"不是。","es":"No.","ar":"لا.","hi":"नहीं।","de":"Nein.","fr":"Non.","it":"No."},
    {"cat":"동의 & 반응","ko":"아마도요.","en":"Maybe.","ja":"たぶん。","zh":"也许。","es":"Quizás.","ar":"ربما.","hi":"शायद।","de":"Vielleicht.","fr":"Peut-être.","it":"Forse."},
    {"cat":"동의 & 반응","ko":"물론이죠.","en":"Of course.","ja":"もちろん。","zh":"当然。","es":"Por supuesto.","ar":"بالطبع.","hi":"बिल्कुल।","de":"Natürlich.","fr":"Bien sûr.","it":"Certo."},
    {"cat":"동의 & 반응","ko":"그런 것 같아요.","en":"I think so.","ja":"そう思います。","zh":"我想是的。","es":"Creo que sí.","ar":"أعتقد ذلك.","hi":"मुझे ऐसा लगता है।","de":"Ich glaube schon.","fr":"Je pense que oui.","it":"Penso di sì."},
    {"cat":"동의 & 반응","ko":"그렇지 않은 것 같아요.","en":"I don’t think so.","ja":"そうは思いません。","zh":"我不这么认为。","es":"No lo creo.","ar":"لا أعتقد ذلك.","hi":"मुझे ऐसा नहीं लगता।","de":"Ich glaube nicht.","fr":"Je ne pense pas.","it":"Non credo."},
    {"cat":"동의 & 반응","ko":"정확해요.","en":"Exactly.","ja":"その通りです。","zh":"完全正确。","es":"Exactamente.","ar":"بالضبط.","hi":"बिल्कुल सही।","de":"Genau.","fr":"Exactement.","it":"Esatto."},
    {"cat":"동의 & 반응","ko":"정말요?","en":"Really?","ja":"本当ですか？","zh":"真的吗？","es":"¿De verdad?","ar":"حقًا؟","hi":"सच में?","de":"Wirklich?","fr":"Vraiment ?","it":"Davvero?"},
    {"cat":"동의 & 반응","ko":"그건 사실이에요.","en":"That’s true.","ja":"それは本当です。","zh":"那是真的。","es":"Eso es cierto.","ar":"هذا صحيح.","hi":"यह सच है।","de":"Das stimmt.","fr":"C’est vrai.","it":"È vero."},
    {"cat":"동의 & 반응","ko":"동의해요.","en":"I agree.","ja":"同意します。","zh":"我同意。","es":"Estoy de acuerdo.","ar":"أنا موافق.","hi":"मैं सहमत हूँ।","de":"Ich stimme zu.","fr":"Je suis d’accord.","it":"Sono d’accordo."},

    # 6 Relationships
    {"cat":"인간관계","ko":"보고 싶어요.","en":"I miss you.","ja":"会いたいです。","zh":"我想你。","es":"Te extraño.","ar":"أفتقدك.","hi":"मुझे तुम्हारी याद आती है।","de":"Ich vermisse dich.","fr":"Tu me manques.","it":"Mi manchi."},
    {"cat":"인간관계","ko":"사랑해요.","en":"I love you.","ja":"愛しています。","zh":"我爱你。","es":"Te quiero.","ar":"أحبك.","hi":"मैं तुमसे प्यार करता हूँ।","de":"Ich liebe dich.","fr":"Je t’aime.","it":"Ti amo."},
    {"cat":"인간관계","ko":"축하해요!","en":"Congratulations!","ja":"おめでとう！","zh":"恭喜！","es":"¡Felicidades!","ar":"تهانينا!","hi":"बधाई हो!","de":"Herzlichen Glückwunsch!","fr":"Félicitations !","it":"Congratulazioni!"},
    {"cat":"인간관계","ko":"행운을 빌어요!","en":"Good luck!","ja":"頑張って！","zh":"祝你好运！","es":"¡Buena suerte!","ar":"حظًا موفقًا!","hi":"शुभकामनाएँ!","de":"Viel Glück!","fr":"Bonne chance !","it":"Buona fortuna!"},
    {"cat":"인간관계","ko":"생일 축하해요!","en":"Happy birthday!","ja":"お誕生日おめでとう！","zh":"生日快乐！","es":"¡Feliz cumpleaños!","ar":"عيد ميلاد سعيد!","hi":"जन्मदिन मुबारक हो!","de":"Alles Gute zum Geburtstag!","fr":"Joyeux anniversaire !","it":"Buon compleanno!"},
    {"cat":"인간관계","ko":"오랜만이에요.","en":"Long time no see.","ja":"久しぶりです。","zh":"好久不见。","es":"Cuánto tiempo sin verte.","ar":"لم نلتقِ منذ زمن طويل.","hi":"काफी समय से नहीं मिले।","de":"Lange nicht gesehen.","fr":"Ça fait longtemps.","it":"Quanto tempo!"},
    {"cat":"인간관계","ko":"나중에 전화해요.","en":"Call me later.","ja":"後で電話して。","zh":"稍后给我打电话。","es":"Llámame más tarde.","ar":"اتصل بي لاحقًا.","hi":"मुझे बाद में कॉल करना।","de":"Ruf mich später an.","fr":"Appelle-moi plus tard.","it":"Chiamami più tardi."},
    {"cat":"인간관계","ko":"가요.","en":"Let’s go.","ja":"行きましょう。","zh":"我们走吧。","es":"Vamos.","ar":"لنذهب.","hi":"चलो चलते हैं।","de":"Lass uns gehen.","fr":"Allons-y.","it":"Andiamo."},
    {"cat":"인간관계","ko":"저와 같이 와요.","en":"Come with me.","ja":"一緒に来て。","zh":"跟我来。","es":"Ven conmigo.","ar":"تعال معي.","hi":"मेरे साथ आओ।","de":"Komm mit mir.","fr":"Viens avec moi.","it":"Vieni con me."},
    {"cat":"인간관계","ko":"내일 봐요.","en":"See you tomorrow.","ja":"また明日。","zh":"明天见。","es":"Hasta mañana.","ar":"أراك غدًا.","hi":"कल मिलते हैं।","de":"Bis morgen.","fr":"À demain.","it":"A domani."},

    # 7 Shopping food
    {"cat":"쇼핑 & 음식","ko":"이걸로 할게요.","en":"I’d like this.","ja":"これをお願いします。","zh":"我想要这个。","es":"Quisiera esto.","ar":"أريد هذا.","hi":"मुझे यह चाहिए।","de":"Ich möchte das hier.","fr":"Je voudrais ceci.","it":"Vorrei questo."},
    {"cat":"쇼핑 & 음식","ko":"카드로 결제할 수 있나요?","en":"Can I pay by card?","ja":"カードで払えますか？","zh":"可以刷卡吗？","es":"¿Puedo pagar con tarjeta?","ar":"هل يمكنني الدفع بالبطاقة؟","hi":"क्या मैं कार्ड से भुगतान कर सकता हूँ?","de":"Kann ich mit Karte bezahlen?","fr":"Puis-je payer par carte ?","it":"Posso pagare con carta?"},
    {"cat":"쇼핑 & 음식","ko":"이거 다른 사이즈 있나요?","en":"Do you have this in another size?","ja":"これの別のサイズはありますか？","zh":"这个有其他尺码吗？","es":"¿Tienen esto en otra talla?","ar":"هل لديكم هذا بمقاس آخر؟","hi":"क्या यह दूसरे साइज़ में है?","de":"Haben Sie das in einer anderen Größe?","fr":"Vous l’avez dans une autre taille ?","it":"Avete questo in un’altra taglia?"},
    {"cat":"쇼핑 & 음식","ko":"음식이 맛있어요.","en":"The food is delicious.","ja":"食べ物がおいしいです。","zh":"食物很好吃。","es":"La comida está deliciosa.","ar":"الطعام لذيذ.","hi":"खाना स्वादिष्ट है।","de":"Das Essen ist lecker.","fr":"La nourriture est délicieuse.","it":"Il cibo è delizioso."},
    {"cat":"쇼핑 & 음식","ko":"계산서 주세요.","en":"Can I get the bill?","ja":"お会計をお願いします。","zh":"可以给我账单吗？","es":"¿Me trae la cuenta?","ar":"هل يمكنني الحصول على الفاتورة؟","hi":"क्या मुझे बिल मिल सकता है?","de":"Kann ich die Rechnung bekommen?","fr":"Puis-je avoir l’addition ?","it":"Posso avere il conto?"},
    {"cat":"쇼핑 & 음식","ko":"물 주세요.","en":"Water, please.","ja":"水をお願いします。","zh":"请给我水。","es":"Agua, por favor.","ar":"ماء من فضلك.","hi":"पानी, कृपया।","de":"Wasser, bitte.","fr":"De l’eau, s’il vous plaît.","it":"Acqua, per favore."},
    {"cat":"쇼핑 & 음식","ko":"배불러요.","en":"I’m full.","ja":"お腹いっぱいです。","zh":"我饱了。","es":"Estoy lleno.","ar":"أنا شبعان.","hi":"मेरा पेट भर गया है।","de":"Ich bin satt.","fr":"Je suis rassasié.","it":"Sono sazio."},
    {"cat":"쇼핑 & 음식","ko":"먹어요.","en":"Let’s eat.","ja":"食べましょう。","zh":"我们吃吧。","es":"Vamos a comer.","ar":"لنأكل.","hi":"चलो खाते हैं।","de":"Lass uns essen.","fr":"Mangeons.","it":"Mangiamo."},
    {"cat":"쇼핑 & 음식","ko":"뭘 추천하세요?","en":"What do you recommend?","ja":"おすすめは何ですか？","zh":"你推荐什么？","es":"¿Qué recomiendas?","ar":"ماذا تنصح؟","hi":"आप क्या सुझाते हैं?","de":"Was empfehlen Sie?","fr":"Que recommandez-vous ?","it":"Che cosa consigli?"},
    {"cat":"쇼핑 & 음식","ko":"이걸 살게요.","en":"I’ll take it.","ja":"これにします。","zh":"我要这个。","es":"Me lo llevo.","ar":"سآخذه.","hi":"मैं इसे ले लूँगा।","de":"Ich nehme es.","fr":"Je le prends.","it":"Lo prendo."},

    # 8 Travel
    {"cat":"이동 & 여행","ko":"우리가 어디에 있죠?","en":"Where are we?","ja":"私たちはどこにいますか？","zh":"我们在哪里？","es":"¿Dónde estamos?","ar":"أين نحن؟","hi":"हम कहाँ हैं?","de":"Wo sind wir?","fr":"Où sommes-nous ?","it":"Dove siamo?"},
    {"cat":"이동 & 여행","ko":"길을 잃었어요.","en":"I’m lost.","ja":"道に迷いました。","zh":"我迷路了。","es":"Estoy perdido.","ar":"أنا تائه.","hi":"मैं रास्ता भटक गया हूँ।","de":"Ich habe mich verlaufen.","fr":"Je suis perdu.","it":"Mi sono perso."},
    {"cat":"이동 & 여행","ko":"거기 어떻게 가요?","en":"How can I get there?","ja":"そこへはどう行けばいいですか？","zh":"我怎么去那里？","es":"¿Cómo puedo llegar allí?","ar":"كيف أصل إلى هناك؟","hi":"मैं वहाँ कैसे पहुँच सकता हूँ?","de":"Wie komme ich dorthin?","fr":"Comment puis-je y aller ?","it":"Come posso arrivarci?"},
    {"cat":"이동 & 여행","ko":"멀어요?","en":"Is it far?","ja":"遠いですか？","zh":"远吗？","es":"¿Está lejos?","ar":"هل هو بعيد؟","hi":"क्या यह दूर है?","de":"Ist es weit?","fr":"C’est loin ?","it":"È lontano?"},
    {"cat":"이동 & 여행","ko":"택시를 타요.","en":"Let’s take a taxi.","ja":"タクシーに乗りましょう。","zh":"我们打车吧。","es":"Tomemos un taxi.","ar":"لنأخذ سيارة أجرة.","hi":"चलो टैक्सी लेते हैं।","de":"Nehmen wir ein Taxi.","fr":"Prenons un taxi.","it":"Prendiamo un taxi."},
    {"cat":"이동 & 여행","ko":"가는 중이에요.","en":"I’m on my way.","ja":"向かっています。","zh":"我在路上。","es":"Estoy en camino.","ar":"أنا في الطريق.","hi":"मैं रास्ते में हूँ।","de":"Ich bin unterwegs.","fr":"Je suis en route.","it":"Sto arrivando."},
    {"cat":"이동 & 여행","ko":"교통이 너무 막혀요.","en":"The traffic is terrible.","ja":"渋滞がひどいです。","zh":"交通太糟糕了。","es":"El tráfico está fatal.","ar":"الازدحام المروري سيئ جدًا.","hi":"ट्रैफिक बहुत खराब है।","de":"Der Verkehr ist schrecklich.","fr":"La circulation est terrible.","it":"Il traffico è terribile."},
    {"cat":"이동 & 여행","ko":"주소가 뭐예요?","en":"What’s the address?","ja":"住所は何ですか？","zh":"地址是什么？","es":"¿Cuál es la dirección?","ar":"ما العنوان؟","hi":"पता क्या है?","de":"Wie lautet die Adresse?","fr":"Quelle est l’adresse ?","it":"Qual è l’indirizzo?"},
    {"cat":"이동 & 여행","ko":"언제 시작해요?","en":"When does it start?","ja":"いつ始まりますか？","zh":"什么时候开始？","es":"¿Cuándo empieza?","ar":"متى يبدأ؟","hi":"यह कब शुरू होता है?","de":"Wann beginnt es?","fr":"Quand est-ce que ça commence ?","it":"Quando inizia?"},
    {"cat":"이동 & 여행","ko":"저 여기 있어요.","en":"I’m here.","ja":"ここにいます。","zh":"我在这里。","es":"Estoy aquí.","ar":"أنا هنا.","hi":"मैं यहाँ हूँ।","de":"Ich bin hier.","fr":"Je suis ici.","it":"Sono qui."},

    # 9 Phone online
    {"cat":"전화 & 온라인","ko":"제 말 들려요?","en":"Can you hear me?","ja":"聞こえますか？","zh":"你听得到我吗？","es":"¿Me oyes?","ar":"هل تسمعني؟","hi":"क्या आप मुझे सुन सकते हैं?","de":"Kannst du mich hören?","fr":"Tu m’entends ?","it":"Mi senti?"},
    {"cat":"전화 & 온라인","ko":"인터넷이 느려요.","en":"The internet is slow.","ja":"インターネットが遅いです。","zh":"网络很慢。","es":"Internet está lento.","ar":"الإنترنت بطيء.","hi":"इंटरनेट धीमा है।","de":"Das Internet ist langsam.","fr":"Internet est lent.","it":"Internet è lento."},
    {"cat":"전화 & 온라인","ko":"링크 보내줘요.","en":"Send me the link.","ja":"リンクを送ってください。","zh":"把链接发给我。","es":"Envíame el enlace.","ar":"أرسل لي الرابط.","hi":"मुझे लिंक भेजो।","de":"Schick mir den Link.","fr":"Envoie-moi le lien.","it":"Mandami il link."},
    {"cat":"전화 & 온라인","ko":"문자할게요.","en":"I’ll text you.","ja":"メッセージします。","zh":"我会给你发短信。","es":"Te escribiré.","ar":"سأرسل لك رسالة.","hi":"मैं तुम्हें मैसेज करूँगा।","de":"Ich schreibe dir.","fr":"Je t’enverrai un message.","it":"Ti scrivo."},
    {"cat":"전화 & 온라인","ko":"이메일 확인해요.","en":"Check your email.","ja":"メールを確認してください。","zh":"查看你的电子邮件。","es":"Revisa tu correo.","ar":"تحقق من بريدك الإلكتروني.","hi":"अपना ईमेल चेक करो।","de":"Überprüf deine E-Mail.","fr":"Vérifie ton e-mail.","it":"Controlla la tua email."},
    {"cat":"전화 & 온라인","ko":"배터리가 다 됐어요.","en":"My battery is dead.","ja":"バッテリーが切れました。","zh":"我的电池没电了。","es":"Se me acabó la batería.","ar":"بطاريتي نفدت.","hi":"मेरी बैटरी खत्म हो गई है।","de":"Mein Akku ist leer.","fr":"Ma batterie est déchargée.","it":"La batteria è scarica."},
    {"cat":"전화 & 온라인","ko":"지금 일하고 있어요.","en":"I’m working right now.","ja":"今仕事中です。","zh":"我现在在工作。","es":"Estoy trabajando ahora mismo.","ar":"أنا أعمل الآن.","hi":"मैं अभी काम कर रहा हूँ।","de":"Ich arbeite gerade.","fr":"Je travaille en ce moment.","it":"Sto lavorando adesso."},
    {"cat":"전화 & 온라인","ko":"확인해볼게요.","en":"Let me check.","ja":"確認します。","zh":"让我看一下。","es":"Déjame revisar.","ar":"دعني أتحقق.","hi":"मुझे जाँचने दो।","de":"Lass mich nachsehen.","fr":"Laissez-moi vérifier.","it":"Fammi controllare."},
    {"cat":"전화 & 온라인","ko":"잠시만 기다려요.","en":"Hold on a second.","ja":"少し待ってください。","zh":"请等一下。","es":"Espera un segundo.","ar":"انتظر لحظة.","hi":"एक सेकंड रुकिए।","de":"Warte kurz.","fr":"Attends une seconde.","it":"Aspetta un secondo."},
    {"cat":"전화 & 온라인","ko":"다시 전화할게요.","en":"I’ll call you back.","ja":"折り返し電話します。","zh":"我稍后回电话。","es":"Te devolveré la llamada.","ar":"سأتصل بك لاحقًا.","hi":"मैं आपको वापस कॉल करूँगा।","de":"Ich rufe dich zurück.","fr":"Je te rappellerai.","it":"Ti richiamo."},

    # 10 Reactions
    {"cat":"감탄 & 이해","ko":"정말 놀라워요.","en":"That’s amazing.","ja":"それはすごいです。","zh":"太棒了。","es":"Eso es increíble.","ar":"هذا مذهل.","hi":"यह कमाल है।","de":"Das ist erstaunlich.","fr":"C’est incroyable.","it":"È fantastico."},
    {"cat":"감탄 & 이해","ko":"웃기네요.","en":"That’s funny.","ja":"それは面白いです。","zh":"真有趣。","es":"Eso es gracioso.","ar":"هذا مضحك.","hi":"यह मज़ेदार है।","de":"Das ist lustig.","fr":"C’est drôle.","it":"È divertente."},
    {"cat":"감탄 & 이해","ko":"흥미롭네요.","en":"That’s interesting.","ja":"それは興味深いです。","zh":"真有意思。","es":"Eso es interesante.","ar":"هذا مثير للاهتمام.","hi":"यह दिलचस्प है।","de":"Das ist interessant.","fr":"C’est intéressant.","it":"È interessante."},
    {"cat":"감탄 & 이해","ko":"말도 안 돼요.","en":"That’s crazy.","ja":"それはすごいですね。","zh":"太疯狂了。","es":"Eso es una locura.","ar":"هذا جنوني.","hi":"यह पागलपन है।","de":"Das ist verrückt.","fr":"C’est fou.","it":"È pazzesco."},
    {"cat":"감탄 & 이해","ko":"모르겠어요.","en":"I don’t know.","ja":"わかりません。","zh":"我不知道。","es":"No lo sé.","ar":"لا أعرف.","hi":"मुझे नहीं पता।","de":"Ich weiß nicht.","fr":"Je ne sais pas.","it":"Non lo so."},
    {"cat":"감탄 & 이해","ko":"이해해요.","en":"I understand.","ja":"わかります。","zh":"我明白。","es":"Entiendo.","ar":"أفهم.","hi":"मैं समझता हूँ।","de":"Ich verstehe.","fr":"Je comprends.","it":"Capisco."},
    {"cat":"감탄 & 이해","ko":"괜찮아요.","en":"It’s okay.","ja":"大丈夫です。","zh":"没关系。","es":"Está bien.","ar":"لا بأس.","hi":"कोई बात नहीं।","de":"Es ist okay.","fr":"Ça va.","it":"Va bene."},
    {"cat":"감탄 & 이해","ko":"걱정하지 마세요.","en":"No worries.","ja":"心配しないで。","zh":"别担心。","es":"No te preocupes.","ar":"لا تقلق.","hi":"चिंता मत करो।","de":"Keine Sorge.","fr":"Ne t’inquiète pas.","it":"Non preoccuparti."},
    {"cat":"감탄 & 이해","ko":"좋아요.","en":"Sounds good.","ja":"いいですね。","zh":"听起来不错。","es":"Suena bien.","ar":"يبدو جيدًا.","hi":"अच्छा लगता है।","de":"Klingt gut.","fr":"Ça me va.","it":"Suona bene."},
    {"cat":"감탄 & 이해","ko":"재밌게 보내요!","en":"Have fun!","ja":"楽しんで！","zh":"玩得开心！","es":"¡Diviértete!","ar":"استمتع!","hi":"मज़े करो!","de":"Viel Spaß!","fr":"Amuse-toi bien !","it":"Divertiti!"},
]

def by_category(category):
    return [p for p in PHRASES if p["cat"] == category]

def lang_label(code):
    return f'{LANGS[code]["flag"]} {LANGS[code]["name"]}'

def phrase_card(i, item, lang):
    direction = LANGS[lang]["dir"]
    text = item[lang]
    english = item["en"]
    korean = item["ko"]
    align = "right" if direction == "rtl" else "left"
    st.markdown(
        f"""
        <div style="border:1px solid #eee; border-radius:18px; padding:18px; margin:10px 0; background:#ffffff;">
            <div style="font-size:14px; color:#888;">#{i}</div>
            <div dir="{direction}" style="text-align:{align}; font-size:28px; font-weight:700; margin:6px 0;">{text}</div>
            <div style="font-size:15px; color:#555;">영어 기준: <b>{english}</b></div>
            <div style="font-size:15px; color:#777;">한국어 뜻: {korean}</div>
        </div>
        """,
        unsafe_allow_html=True,
    )

def init_state():
    if "page" not in st.session_state:
        st.session_state.page = "home"
    if "lang" not in st.session_state:
        st.session_state.lang = None
    if "quiz_score" not in st.session_state:
        st.session_state.quiz_score = 0
    if "quiz_total" not in st.session_state:
        st.session_state.quiz_total = 0
    if "quiz_item" not in st.session_state:
        st.session_state.quiz_item = random.choice(PHRASES)
    if "quiz_mode" not in st.session_state:
        st.session_state.quiz_mode = "뜻 맞히기"

def new_quiz():
    st.session_state.quiz_item = random.choice(PHRASES)

def header():
    st.markdown("""
    <style>
    .main-title {font-size:44px; font-weight:900; margin-bottom:0;}
    .sub {font-size:18px; color:#666; margin-top:4px;}
    .big-button button {height:72px; border-radius:18px; font-size:18px;}
    </style>
    """, unsafe_allow_html=True)
    st.markdown('<div class="main-title">🌍 Daily 100 Phrases</div>', unsafe_allow_html=True)
    st.markdown('<div class="sub">10개 언어로 배우는 전세계 일상 표현 100개</div>', unsafe_allow_html=True)

def home():
    header()
    st.divider()
    st.subheader("1. 학습할 언어를 선택하세요")
    cols = st.columns(5)
    for idx, code in enumerate(LANGS.keys()):
        with cols[idx % 5]:
            if st.button(lang_label(code), key=f"lang_{code}", use_container_width=True):
                st.session_state.lang = code
                st.session_state.page = "categories"
                st.rerun()

    st.divider()
    st.subheader("2. 바로 퀴즈로 가기")
    st.write("랜덤 문장으로 뜻 맞히기, 표현 맞히기, 카드 학습을 할 수 있어요.")
    if st.button("🎯 퀴즈 시작하기", use_container_width=True):
        st.session_state.page = "quiz"
        st.rerun()

def categories():
    lang = st.session_state.lang
    st.button("← 메인으로", on_click=lambda: st.session_state.update(page="home"))
    st.title(f"{lang_label(lang)} 카테고리 선택")
    st.write("카테고리를 고르면 해당 언어의 자주 쓰는 문장 10개가 나옵니다.")
    cols = st.columns(2)
    for idx, cat in enumerate(CATEGORIES):
        with cols[idx % 2]:
            if st.button(f"📌 {cat}", key=f"cat_{cat}", use_container_width=True):
                st.session_state.category = cat
                st.session_state.page = "learn"
                st.rerun()

def learn():
    lang = st.session_state.lang
    cat = st.session_state.category
    c1, c2, c3 = st.columns([1,1,4])
    with c1:
        if st.button("← 언어 선택"):
            st.session_state.page = "home"
            st.rerun()
    with c2:
        if st.button("← 카테고리"):
            st.session_state.page = "categories"
            st.rerun()
    st.title(f"{lang_label(lang)} · {cat}")
    st.caption("각 카드에는 선택한 언어 표현, 영어 기준 표현, 한국어 뜻이 함께 표시됩니다.")
    for i, item in enumerate(by_category(cat), 1):
        phrase_card(i, item, lang)

    st.divider()
    if st.button("이 카테고리로 퀴즈 풀기 🎯", use_container_width=True):
        st.session_state.quiz_lang = lang
        st.session_state.quiz_category = cat
        st.session_state.page = "quiz"
        new_quiz()
        st.rerun()

def quiz():
    st.button("← 메인으로", on_click=lambda: st.session_state.update(page="home"))
    st.title("🎯 퀴즈 모드")

    lang = st.selectbox(
        "퀴즈 언어",
        list(LANGS.keys()),
        format_func=lang_label,
        index=list(LANGS.keys()).index(st.session_state.get("quiz_lang", st.session_state.lang or "en"))
    )
    category = st.selectbox("카테고리", ["전체"] + CATEGORIES, index=0)
    mode = st.radio("퀴즈 방식", ["뜻 맞히기", "표현 맞히기", "랜덤 카드"], horizontal=True)
    pool = PHRASES if category == "전체" else by_category(category)

    if "quiz_pool_key" not in st.session_state or st.session_state.quiz_pool_key != f"{lang}-{category}-{mode}":
        st.session_state.quiz_pool_key = f"{lang}-{category}-{mode}"
        st.session_state.quiz_item = random.choice(pool)

    item = st.session_state.quiz_item
    if item not in pool:
        item = random.choice(pool)
        st.session_state.quiz_item = item

    st.info(f"점수: {st.session_state.quiz_score} / {st.session_state.quiz_total}")

    if mode == "랜덤 카드":
        phrase_card(1, item, lang)
        st.write("소리 내어 3번 읽고, 한국어 뜻을 가린 상태로 다시 말해보세요.")
        if st.button("다음 카드"):
            st.session_state.quiz_item = random.choice(pool)
            st.rerun()
        return

    if mode == "뜻 맞히기":
        direction = LANGS[lang]["dir"]
        st.markdown(f'<div dir="{direction}" style="font-size:36px; font-weight:800; padding:18px 0;">{item[lang]}</div>', unsafe_allow_html=True)
        correct = item["ko"]
        wrongs = random.sample([p["ko"] for p in PHRASES if p["ko"] != correct], 3)
        options = wrongs + [correct]
        random.shuffle(options)
        answer = st.radio("이 표현의 한국어 뜻은?", options, index=None)
    else:
        st.markdown(f"### 뜻: **{item['ko']}**")
        correct = item[lang]
        wrongs = random.sample([p[lang] for p in PHRASES if p[lang] != correct], 3)
        options = wrongs + [correct]
        random.shuffle(options)
        answer = st.radio("알맞은 표현은?", options, index=None)

    col1, col2 = st.columns(2)
    with col1:
        if st.button("정답 확인", use_container_width=True, disabled=answer is None):
            st.session_state.quiz_total += 1
            if answer == correct:
                st.session_state.quiz_score += 1
                st.success("정답이에요! 🎉")
            else:
                st.error(f"아쉬워요. 정답은: {correct}")
            st.caption(f"영어 기준 표현: {item['en']}")
    with col2:
        if st.button("다음 문제", use_container_width=True):
            st.session_state.quiz_item = random.choice(pool)
            st.rerun()

def sidebar():
    with st.sidebar:
        st.title("메뉴")
        if st.button("🏠 메인", use_container_width=True):
            st.session_state.page = "home"
            st.rerun()
        if st.button("🎯 퀴즈", use_container_width=True):
            st.session_state.page = "quiz"
            st.rerun()
        st.divider()
        st.caption("배포 방법")
        st.code("streamlit run app.py")
        st.caption("Streamlit Community Cloud에 올릴 때 requirements.txt에는 streamlit만 적으면 됩니다.")

init_state()
sidebar()

if st.session_state.page == "home":
    home()
elif st.session_state.page == "categories":
    categories()
elif st.session_state.page == "learn":
    learn()
elif st.session_state.page == "quiz":
    quiz()

