# lostglade
Лучший в мире сервер.

## Базовые параметры
- Minecraft: `1.21.11`
- IP: `marchtaylor.ddns.net:25565`
- Роутер: проброс `25565 -> 192.168.31.10:25565`
- Тип: Fabric, только `server-side` моды

## Основные моды сервера
- `fabric-api`
- `cardinal-components-api`
- `polymer-bundled`

## Зависимости мода
Зависимости зафиксированы в `build.gradle` и `fabric.mod.json`:
- Fabric API
- Polymer Core
- Polymer Resource Pack
- Cardinal Components API


- мод хранить в виде папки и запускать только как dev сервер без сборки мода в директории основного сервера lostglade через gradle

## Dev запуск сервера
- Команда запуска: `cd ~/Desktop/lostglade/mods/lg2-0.1.0 && ./gradlew runServer`
- Скрипт запуска: `cd ~/Desktop/lostglade/mods/lg2-0.1.0 && ./scripts/run-dev-server.sh`
- `runServer` запускается из корня сервера: `~/Desktop/lostglade`

## Серверный ресурспак
- Исходник пустого pack хранится как папка: `mods/lg2-0.1.0/resourcepack/`
- Перед запуском gradle-задача `prepareDevResourcePack` копирует его в `polymer/source_assets/`
- Polymer AutoHost включён в `config/polymer/auto-host.json`, поэтому клиенту предлагается загрузка ресурспака при заходе
