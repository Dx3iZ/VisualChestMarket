# VisualChestMarket
VisualChestMarket, oyuncularınızın sandıklarını kullanarak kolayca market oluşturmasını sağlayan, görsel açıdan zengin ve kullanımı basit bir Spigot/Paper eklentisidir.

![Banner](https://github.com/Dx3iZ/VisualChestMarket/blob/main/2026-02-26_21.11.34.png)
# 📦 VisualChestMarket - Modern ve Görsel Sandık Marketi

VisualChestMarket, oyuncularınızın sandıklarını kullanarak kolayca market oluşturmasını sağlayan, görsel açıdan zengin ve kullanımı basit bir Spigot/Paper eklentisidir. Hologramlar, zırh askıları ve kullanıcı dostu arayüzü ile sunucunuzun ekonomisini canlandırın!

## ✨ Öne Çıkan Özellikler

*   **Görsel Zenginlik:**
    *   **Hologram Desteği:** Satılan eşyanın adı ve fiyatı sandığın üzerinde hologram olarak görünür.
    *   **3D Eşya Gösterimi:** Eşyalar sandığın üzerinde döner (ArmorStand veya ItemDisplay).
    *   **Özelleştirilebilir Tabelalar:** Market tabelasındaki tüm satırları ve renkleri config üzerinden ayarlayabilirsiniz.

*   **Kolay Kullanım:**
    *   **Hızlı Kurulum:** Eşya ile sandığa eğilerek sol tıklayın, fiyatı girin ve marketiniz hazır!
    *   **GUI Menüsü:** Market ayarlarını (fiyat, mod, görünüm) kolayca yönetebileceğiniz bir menü.
    *   **Bakarak İşlem:** Komutlarla uğraşmak yerine markete bakarak `/vcm` komutlarını kullanabilirsiniz.

*   **Gelişmiş Ekonomi ve Yönetim:**
    *   **Vergi Sistemi:** Market işlemlerinden otomatik komisyon (vergi) kesintisi yapabilirsiniz.
    *   **Limitler:** Oyuncu başına market limiti ve chunk başına market limiti belirleyebilirsiniz.
    *   **Blacklist:** Belirli eşyaların satışını yasaklayabilirsiniz.
    *   **Geçmiş Kaydı:** Tüm alış/satış işlemleri kaydedilir ve GUI üzerinden görüntülenebilir.

*   **Güvenlik ve Koruma:**
    *   **Grief Koruması:** Market sandıkları başkaları tarafından kırılamaz veya açılamaz.
    *   **Yasaklama (Ban) Sistemi:** İstemediğiniz oyuncuların marketinizden alışveriş yapmasını engelleyebilirsiniz.
    *   **Üye Sistemi:** Marketinize yardımcılar (Helper) veya yöneticiler (Manager) ekleyerek ortak yönetim sağlayabilirsiniz.

*   **Admin Dostu:**
    *   **Transfer:** Marketleri veya bir oyuncunun tüm marketlerini başkasına devredebilirsiniz.
    *   **Tam Kontrol:** Adminler tüm marketleri yönetebilir, silebilir veya düzenleyebilir.
    *   **Veritabanı:** SQLite (varsayılan) ve MySQL desteği ile verileriniz güvende.

## 🛠️ Gereksinimler (Dependencies)

Bu eklentinin çalışması için sunucunuzda aşağıdaki eklentilerin yüklü olması **ZORUNLUDUR**:

1.  **Vault:** Ekonomi işlemleri için gereklidir.
2.  **Bir Ekonomi Eklentisi:** EssentialsX, CMI, Economy vb. (Vault ile uyumlu herhangi bir ekonomi eklentisi).
3.  **DecentHolograms:** Hologramların görünmesi için gereklidir.

## 🚀 Kurulum

1.  `VisualChestMarket.jar` dosyasını sunucunuzun `plugins` klasörüne atın.
2.  Sunucuyu yeniden başlatın.
3.  `plugins/VisualChestMarket/config.yml` dosyasını açarak ayarları (vergi oranı, limitler, mesajlar vb.) düzenleyin.
4.  `/vcm reload` komutu ile değişiklikleri uygulayın.

## 🎮 Nasıl Kullanılır?

### Oyuncular İçin:
*   **Market Oluşturma:** Satmak istediğiniz eşyayı elinize alın, bir sandığa **eğilerek sol tıklayın** ve sohbetten fiyatı girin.
*   **Market Menüsü:** Kendi marketinize **eğilerek sağ tıklayın**.
*   **Satın Alma:** Başkasının marketine **sol tıklayın** ve miktarı girin (veya `all` yazın).
*   **Üye Ekleme:** `/vcm addmember <oyuncu> <rol>` (Roller: MANAGER, HELPER)
*   **Yasaklama:** `/vcm ban <oyuncu>`

### Adminler İçin:
*   **Tüm Marketleri Devretme:** `/vcm transferall <eski_sahip> <yeni_sahip>`
*   **Marketi Silme:** `/vcm delete` (Baktığınız marketi siler)
*   **Yenileme:** `/vcm reload`

## 📋 Komut Listesi

| Komut | Açıklama | Yetki |
| :--- | :--- | :--- |
| `/vcm menu` | Baktığınız marketin menüsünü açar. | - |
| `/vcm setprice` | Baktığınız marketin fiyatını günceller. | - |
| `/vcm togglemode` | Alış/Satış modunu değiştirir. | - |
| `/vcm toggledisplay` | Görünüm modunu değiştirir (Hologram/ArmorStand). | - |
| `/vcm history` | İşlem geçmişini gösterir. | - |
| `/vcm members` | Market üyelerini yönetir. | - |
| `/vcm banlist` | Yasaklı oyuncuları yönetir. | - |
| `/vcm transfer <oyuncu>` | Baktığınız marketi devreder. | - |
| `/vcm transferall <yeni>` | Tüm marketlerinizi devreder. | - |
| `/vcm delete` | Baktığınız marketi siler. | - |
| `/vcm reload` | Config ve marketleri yeniler. | `vcm.admin` |
| `/vcm transferall <eski> <yeni>` | Başkasının marketlerini devreder. | `vcm.admin` |

![Banner](https://github.com/Dx3iZ/VisualChestMarket/blob/main/Screenshot_1.png)
![Banner](https://github.com/Dx3iZ/VisualChestMarket/blob/main/Screenshot_2.png)

