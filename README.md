# SpectatorToggle
# Falcon Code Team

بلوقن **Paper** يضيف وضع مشاهدة مؤقتًا عبر الأمر `/spec`، مع نظام رتبة داخلي باسم **Spectator** وأمر خاص للانتقال إلى لاعب موجود في وضع المشاهدة.

## نطاق التوافق

الإصدار `1.2.1` مبني على Bukkit/Spigot API قديمة ومستقرة مع bytecode متوافق مع **Java 8**، ولذلك يستهدف ملف JAR واحدًا لخوادم **Paper 1.16.5 إلى Paper 1.21.x**، بما في ذلك Paper 1.21.11. تم اختبار تحميل النسخة الجديدة على Paper 1.21.11، بينما يعتمد التشغيل على API المشتركة الموجودة في النطاق المذكور.

> لا أضمن تشغيل النسخة على Paper 1.12 أو أقدم. إذا احتجت دعم 1.8–1.12، فالأفضل إصدار فرعي منفصل بسبب اختلافات API وملفات `plugin.yml`.

| العنصر | القيمة |
|---|---|
| Minecraft / Paper الأقدم المستهدف | 1.16.5 |
| Minecraft / Paper الحديث المدعوم | 1.21.x، وتم الاختبار على 1.21.11 |
| Java المطلوبة لتشغيل الخادم | حسب متطلبات إصدار Paper المستخدم |
| Java المطلوبة للبناء من المصدر | Java 8 أو أحدث |
| ملف JAR | `spectator-toggle-1.2.1.jar` |

اعتمد المشروع على Bukkit API المشتركة بدل خصائص Paper الحديثة الخاصة بإصدار واحد. كما تم استخدام صياغة Java 8 وإزالة الاعتماد على `Adventure Component` وواجهات الكائنات التي لا توجد في API القديمة. توضح وثائق Paper أن قيمة `api-version` تحدد الحد الأدنى للإصدار الذي يسمح الخادم بتحميل البلوقن عليه [1]، كما تحذّر وثائق API من افتراض ثبات كل التفاصيل عبر الإصدارات الرئيسية [2].

## المزايا

عند استخدام `/spec` يحفظ البلوقن العالم والموقع واتجاه النظر، ثم يحول اللاعب إلى وضع `Spectator`. عند استخدام الأمر مرة ثانية، يعود اللاعب إلى المكان المحفوظ ويتحول دائمًا إلى `Survival`، بغض النظر عن وضعه قبل تفعيل المشاهدة.

يحفظ البلوقن حالات المشاهدة في `config.yml` حتى لا تضيع أثناء إعادة التشغيل الطبيعية. وإذا كان اللاعب في وضع `Spectator` فعلًا عند إعادة تشغيل الخادم أو بعد تعطل مفاجئ، تتم محاولة إعادته إلى مكانه المحفوظ ثم تحويله إلى `Survival`. أما إذا كان اللاعب Survival عند الإيقاف، فلا تتم استعادة حالة Spectator قديمة له.

عند دخول اللاعب، يتم تحويله إلى `Survival` إذا كان في Spectator أو Adventure أو أي وضع آخر غير Creative، بينما يبقى اللاعب الموجود في Creative في Creative. ويتم حذف سجلات Spectator القديمة عند الدخول حتى لا تتسبب في نقله بشكل غير متوقع.

يتضمن البلوقن نظام رتبة داخليًا يمكن من خلاله تحديد ما إذا كان `/spec` متاحًا لجميع اللاعبين أو لأعضاء رتبة Spectator فقط. كما يتيح الأمر `/specgoto` لأعضاء الرتبة الانتقال إلى الموقع الحالي للاعب آخر موجود في وضع المشاهدة.

## التثبيت

1. نزّل ملف `spectator-toggle-1.2.1.jar` من صفحة الإصدار في GitHub.
2. انسخ الملف إلى مجلد `plugins` داخل خادم Paper.
3. احذف أي نسخة أقدم من `SpectatorToggle` حتى لا يتم تحميل نسختين.
4. شغّل الخادم أو أعد تشغيله.
5. تحقق من سجل الخادم من ظهور `SpectatorToggle v1.2.1` ثم رسالة التفعيل.

لا يحتاج البلوقن إلى مكتبات خارجية. ملف JAR واحد يكفي للتثبيت.

## أمر وضع المشاهدة

```text
/spec
```

عند الاستخدام الأول، يتم حفظ موقع اللاعب وتحويله إلى `SPECTATOR`. عند الاستخدام الثاني، يعود اللاعب إلى الموقع المحفوظ ويتحول إلى `SURVIVAL`.

صلاحية الأمر هي `spectator.use`. في وضع الوصول العام تكون الصلاحية مفعلة افتراضيًا لجميع اللاعبين، بينما في وضع الرتبة يتحقق البلوقن من عضوية اللاعب في رتبة Spectator.

## نظام رتبة Spectator

هذه الرتبة داخلية خاصة بالبلوقن. هي لا تظهر تلقائيًا بجانب اسم اللاعب في الدردشة أو قائمة اللاعبين، ولا تستبدل أنظمة الرتب مثل LuckPerms. الغرض منها هو تحديد من يستطيع استخدام `/spec` و`/specgoto`.

### أوامر الإدارة

| الأمر | الوظيفة | من يستطيع استخدامه |
|---|---|---|
| `/specadmin create` | إنشاء رتبة Spectator داخل البلوقن | OP أو `spectator.admin` |
| `/specadmin mode all` | جعل `/spec` متاحًا لجميع اللاعبين | OP أو `spectator.admin` |
| `/specadmin mode role` | جعل `/spec` متاحًا لأعضاء الرتبة فقط | OP أو `spectator.admin` |
| `/specadmin add <player>` | إضافة لاعب إلى رتبة Spectator | OP أو `spectator.admin` |
| `/specadmin remove <player>` | إزالة لاعب من رتبة Spectator | OP أو `spectator.admin` |
| `/specadmin list` | عرض أعضاء الرتبة | OP أو `spectator.admin` |

### إعداد الرتبة لأول مرة

نفّذ الأوامر التالية من كونسول السيرفر أو من حساب OP:

```text
/specadmin create
/specadmin add PlayerName
/specadmin mode role
```

استبدل `PlayerName` باسم اللاعب المطلوب. بعد ذلك يستطيع اللاعب استخدام `/spec`.

لجعل `/spec` متاحًا لجميع اللاعبين:

```text
/specadmin mode all
```

ولإعادته إلى أعضاء الرتبة فقط:

```text
/specadmin mode role
```

## أمر الانتقال إلى لاعب Spectator

```text
/specgoto <اسم اللاعب>
```

ينقل هذا الأمر عضو رتبة Spectator إلى **الموقع الحالي** للاعب المحدد، بشرط أن يكون اللاعب متصلًا وفي وضع `Spectator` فعلًا. الأمر ليس متاحًا للعامة، حتى لو كان `/spec` مضبوطًا على الوضع العام.

مثال:

```text
/specgoto PIXC1
```

## الإعدادات

بعد تشغيل البلوقن، يتم إنشاء الملف التالي:

```text
plugins/SpectatorToggle/config.yml
```

الإعداد الافتراضي يكون مشابهًا لما يلي:

```yaml
spec-access: all
spectator-role:
  created: false
  members: []
players: {}
```

القيمة `spec-access` تقبل:

| القيمة | السلوك |
|---|---|
| `all` | أمر `/spec` متاح لجميع اللاعبين |
| `role` | أمر `/spec` متاح لأعضاء رتبة Spectator فقط |

يفضل تغيير الإعداد باستخدام أوامر `/specadmin` بدل تعديل قائمة الأعضاء يدويًا، حتى يتم حفظ UUID الصحيح لكل لاعب.

## الصلاحيات

| الصلاحية | الاستخدام | الافتراضي |
|---|---|---|
| `spectator.use` | صلاحية `/spec` عندما يكون الوصول عامًا | جميع اللاعبين |
| `spectator.admin` | إدارة الرتبة وإعداد الوصول عبر `/specadmin` | OP فقط |

إذا كنت تستخدم LuckPerms، يمكنك منح صلاحية الإدارة مثلًا عبر:

```text
/lp user PlayerName permission set spectator.admin true
```

## البناء من المصدر

يتطلب البناء Java 8 أو أحدث وMaven. ينفذ المشروع البناء باستخدام `spigot-api:1.16.5-R0.1-SNAPSHOT` كأقدم API مشتركة، بينما يعمل الملف الناتج على خوادم Paper ضمن نطاق التوافق المذكور.

نفّذ من مجلد المشروع:

```bash
mvn clean package
```

سيتم إنشاء الملف:

```text
target/spectator-toggle-1.2.1.jar
```

تحقق من أن إصدار bytecode هو Java 8 عبر:

```bash
javap -verbose -classpath target/spectator-toggle-1.2.1.jar \
  com.example.spectatortoggle.SpectatorTogglePlugin | grep 'major version'
```

يجب أن تظهر القيمة:

```text
major version: 52
```

## هيكل المشروع

```text
spectator-toggle/
├── pom.xml
├── README.md
├── compatibility-notes.md
├── spectator-toggle-1.2.1.jar
└── src/
    └── main/
        ├── java/com/example/spectatortoggle/SpectatorTogglePlugin.java
        └── resources/
            ├── config.yml
            └── plugin.yml
```

## ملاحظات الأمان

إذا كان الخادم يعمل بوضع `online-mode=false` للسماح بالدخول من العملاء غير الموثقين، فلا تفتحه للعامة بدون حماية. هذا الوضع يسمح بانتحال أسماء اللاعبين. يفضل استخدام نظام تسجيل دخول مثل AuthMe، وتفعيل whitelist، وعدم إعطاء OP إلا للحسابات الموثوقة.

## المراجع

[1]: https://docs.papermc.io/paper/dev/plugin-yml/ "PaperMC plugin.yml documentation"
[2]: https://jd.papermc.io/paper "Paper API documentation"
[3]: https://docs.papermc.io/paper/dev/project-setup/ "PaperMC project setup documentation"

## الترخيص

يمكن استخدام البلوقن وتعديله لخادمك. هذا المشروع مخصص لخوادم Paper ضمن نطاق التوافق الموضح أعلاه.
