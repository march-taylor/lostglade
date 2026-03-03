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

## Кастомный мод: Lostglade Bitcoin
серверный предмет `lostglade:bitcoin` через Polymer:
- базовый клиентский предмет: `minecraft:gold_ingot`
- текстура: `src/main/resources/assets/lostglade/textures/item/bitcoin.png`
- item model id: `lostglade:bitcoin`

## Зависимости мода
Зависимости зафиксированы в `build.gradle` и `fabric.mod.json`:
- Fabric API
- Polymer Core
- Polymer Resource Pack
- Cardinal Components API


- мод хранить в виде папки
