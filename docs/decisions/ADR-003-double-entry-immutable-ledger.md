# ADR-003: Değiştirilemez Çift Taraflı (Double-Entry) Sıfır-Toplamlı Defter

## Durum
**KABUL EDİLDİ**

## Tarih
2026-09-05

## Bağlam
Basit e-ticaret sistemlerinde bakiye güncellemeleri doğrudan bir hesap tablosundaki sütunun artırılması veya azaltılması (`UPDATE accounts SET balance = balance - 100`) şeklinde yapılır. Ancak finansal bir ödeme platformunda bu yaklaşım:
1. Paranın nereden gelip nereye gittiğini kanıtlayamaz (denetim eksikliği).
2. Sistem çökmelerinde veya yazılım hatalarında bakiye kaçaklarını tespit etmeyi imkansız kılar.
3. Geçmişe dönük mali tablo ve banka mutabakatı çıkarılmasını engeller.

## Karar
SmartPay sisteminde Luca Pacioli'nin 500 yıllık **Çift Taraflı Muhasebe (Double-Entry Bookkeeping)** prensibinin uygulanmasına karar verilmiştir:
1. **Sıfır-Toplam Değişmezi**: Her işlem fişinde (`journal_transactions`) yer alan borç (`DEBIT`) ve alacak (`CREDIT`) satırlarının toplamı birbirine tam eşit olmak zorundadır:
   $$\sum \text{Debit} = \sum \text{Credit}$$
2. **Append-Only (Yalnızca Ekleme)**: Muhasebe satırları (`journal_entries`) asla güncellenemez veya silinemez. Veritabanı tetikleyicisi (`trg_prevent_ledger_modification`) ile `UPDATE` ve `DELETE` engellenmiştir.
3. **Maddi Bakiye Önbelleği (Materialized Balance)**: Hızlı bakiye sorguları için `account_balances` tablosu pessimistic write lock altında yevmiye fişleriyle atomik olarak senkronize edilir.

## Sonuçlar
* **Olumlu**: %100 finansal denetlenebilirlik, matematiksel olarak kanıtlanabilir bakiye tutarlılığı, geçmişe dönük nokta atışı mali raporlama.
* **Olumsuz**: Veritabanı disk kullanımı tekil bakiye güncellemesine göre daha hızlı büyür (ancak PostgreSQL partition stratejileriyle kolayca ölçeklenebilir).
