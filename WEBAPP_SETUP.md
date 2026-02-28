# Настройка Web App для модерации

Telegram Web App требует HTTPS. Для разработки используйте один из туннелей.

## Вариант 1: LocalTunnel (работает в РФ)

1. Установите Node.js (если нет): https://nodejs.org/

2. Установите localtunnel:
```bash
npm install -g localtunnel
```

3. Запустите туннель:
```bash
lt --port 8080
```

4. Скопируйте HTTPS URL (например: `https://funny-cat-12.loca.lt`)

5. Добавьте в `.env`:
```
WEBAPP_BASE_URL=https://funny-cat-12.loca.lt
```

6. Перезапустите приложение

## Вариант 2: Cloudflare Tunnel (работает в РФ)

1. Скачайте cloudflared: https://developers.cloudflare.com/cloudflare-one/connections/connect-apps/install-and-setup/installation/

2. Запустите туннель:
```bash
cloudflared tunnel --url http://localhost:8080
```

3. Скопируйте HTTPS URL из вывода

4. Добавьте в `.env`:
```
WEBAPP_BASE_URL=https://your-url.trycloudflare.com
```

5. Перезапустите приложение

## Вариант 3: Serveo (самый простой, работает в РФ)

1. Запустите через SSH (без установки):
```bash
ssh -R 80:localhost:8080 serveo.net
```

2. Скопируйте HTTPS URL из вывода

3. Добавьте в `.env`:
```
WEBAPP_BASE_URL=https://your-subdomain.serveo.net
```

4. Перезапустите приложение

## Вариант 4: Свой VPS с доменом

Если у вас есть VPS и домен:
1. Настройте Nginx с SSL (Let's Encrypt)
2. Пробросьте порт или используйте reverse proxy
3. Используйте свой домен в `WEBAPP_BASE_URL`

## Использование:

После настройки используйте команду `/edit <ID>` в AdminBot

## Примечание:
- Бесплатные туннели создают новый URL при каждом запуске
- LocalTunnel может показывать предупреждение при первом открытии (нажмите "Continue")
- Для продакшена используйте свой домен с SSL сертификатом
