**Архитектурная схема**

* UI → [Отправляем файл]
* → Spring Boot [Получаем файл] →
* → Создаем или проверяем есть ли таблица по имени файла(пользователя/создателя ?)→
* → Excel → распарсить →
* → Создаем или вставляем данные в новую таблицу (если кол-во столбцов изменилось ?)

Excel: "№ договора"
↓
NameUtils → "no_dogovora" → имя столбца в БД
↓
MetadataService сохраняет:
internal_name = "no_dogovora"
display_name  = "№ договора"
↓
UI показывает: "№ договора"

| Variable        | Пример значения |
| --------------- | --------------- |
| POSTGRES_PASSWORD | `postgres`   |
| POSTGRES_USER   | `tableowner`    |
| POSTGRES_DB     | `tablebuilder`  |


подключиться к бд из контейнера
docker exec -it a3c161c4d9cd bash
psql --username=tableowner --dbname=tablebuilder
\connect improplan

SELECT * FROM first_itog;
 