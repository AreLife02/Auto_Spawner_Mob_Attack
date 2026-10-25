# Auto Spawner Mob Attack

Minecraft **1.21.4** için Fabric istemci modu. Spawner farmlarında hesabın durup mob'lara vurmasını sağlar.

> **Sadece izin veren sunucularda kullan.** Çoğu sunucuda otomatik vuruş kural dışıdır. Kullanmadan önce sunucu yetkililerinden izin al.

## Ne yapar
- **F10** ile aç/kapa, **K** ile menüyü açar.
- Menüden seçtiğin canlıya (varsayılan: Blaze) yürür, bakar ve vurur.
- Oyunculara ve bloklara dokunmaz.
- Açlık eşiğin altına inince **8. veya 9. slottaki altın havucu** yer, **1. slottaki kılıca** geri döner.
- Havuç bitince kendini kapatır ve ekranda (sohbette değil) uyarı gösterir.
- Canlı yokken yakındaki düşen eşyaları toplar.

## Menü
- **Canlılar:** arama kutusu, spawn yumurtası ikonları, açık/kapalı anahtar. Seçililer üstte toplanır.
- **Ayarlar:** arama menzili, vuruş mesafesi, açlık eşiği, eşya toplama.
- **Durum:** açık/kapalı, vuruş sayısı, kalan altın havuç.

Ayarlar `config/auto_spawner_mob_attack.json` dosyasına otomatik kaydedilir.

## Kurulum
1. Fabric Loader ve [Fabric API](https://modrinth.com/mod/fabric-api) (1.21.4) kur.
2. Bu modun `.jar` dosyasını `mods` klasörüne at.

## Derleme
Her `push`'ta GitHub Actions modu derler. **Actions** sekmesi → son çalışma → **Artifacts** → `auto-spawner-mob-attack`.
Elle derlemek için: `gradle build` (Java 21 gerekir). Jar `build/libs/` içinde çıkar.

## Notlar
- Mod her canlıya ayrı davranmaz. Creeper gibi patlayanlara ya da ok atan iskeletlere yaklaşmak canını düşürebilir.
- Yalnızca 1.21.4 için derlenmiştir.
