# OptiClass RASA ChatBot

## Kurulum (Ubuntu VM'de)

```bash
# 1. Python sanal ortam oluştur
cd ~
python3 -m venv rasa-env
source rasa-env/bin/activate

# 2. RASA kur
pip install rasa

# 3. Proje dosyalarını kopyala (Windows'tan SCP)
# scp -r C:\...\rasa_chatbot vboxuser@<IP>:/home/vboxuser/opticlass-chatbot

# 4. Modeli eğit
cd ~/opticlass-chatbot
rasa train

# 5. Servisi başlat (port 5005)
rasa run --enable-api --cors "*" --port 5005
```

## Çalıştığını Doğrula

```bash
curl -X POST http://localhost:5005/webhooks/rest/webhook \
  -H "Content-Type: application/json" \
  -d '{"sender": "test", "message": "hello"}'
```

## FastAPI Proxy

FastAPI `/chatbot` endpoint'i bu servisi `http://localhost:5005` üzerinden çağırır.
Android doğrudan RASA'ya değil, FastAPI'ye istek atar (auth korumalı).

## Yeni Intent Ekleme

1. `data/nlu.yml`'e intent + örnekler ekle
2. `domain.yml`'e intent adı ve `utter_<intent>` cevabı ekle
3. `data/rules.yml`'e tek satır rule ekle
4. `rasa train` ile yeniden eğit, servisi yeniden başlat
