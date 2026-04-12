# Confluence MCP Server для KIRO

MCP-сервер для полного CRUD-доступа к Confluence — разработан для [KIRO](https://kiro.dev) (AI IDE от AWS).

Полная поддержка Unicode: страницы, комментарии, лейблы и вложения работают с **английским**, **ивритом (עברית)** и любым миксом.

---

## Инструменты (27 штук)

### Spaces (5)

| Инструмент     | Описание                     |
|----------------|------------------------------|
| `list_spaces`  | Список всех spaces           |
| `get_space`    | Детали space по ключу        |
| `create_space` | Создать space                |
| `update_space` | Обновить имя / описание      |
| `delete_space` | Удалить space                |

### Страницы — стандартный CRUD (7)

| Инструмент          | Описание                                       |
|---------------------|-------------------------------------------------|
| `list_pages`        | Список страниц в space                          |
| `get_page`          | Получить страницу по ID (с полным телом)        |
| `get_page_metadata` | Получить title, version, space (без тела)       |
| `create_page`       | Создать страницу                                |
| `update_page`       | Заменить всё тело страницы                      |
| `delete_page`       | Удалить страницу (в корзину)                    |
| `search_pages`      | Поиск через CQL (поддерживает иврит)            |

### Страницы — работа с большими страницами (5)

Эти инструменты редактируют страницы **без загрузки полного тела в контекстное окно LLM**. Сервер сам получает страницу, применяет изменение и записывает обратно. Через контекст проходит только дельта.

| Инструмент                | Описание                                                  |
|--------------------------|-----------------------------------------------------------|
| `get_page_excerpt`       | Plain-text превью (по умолчанию 2000 символов)            |
| `append_to_page`         | Добавить HTML в конец страницы                            |
| `prepend_to_page`        | Добавить HTML в начало страницы                           |
| `replace_in_page`        | Найти и заменить текст/HTML в теле (аналог sed)           |
| `replace_section_in_page`| Заменить целую секцию по заголовку                        |

### Комментарии (5)

| Инструмент       | Описание                   |
|------------------|----------------------------|
| `list_comments`  | Список комментариев        |
| `get_comment`    | Получить комментарий по ID |
| `create_comment` | Добавить комментарий       |
| `update_comment` | Обновить комментарий       |
| `delete_comment` | Удалить комментарий        |

### Лейблы (3)

| Инструмент     | Описание              |
|----------------|-----------------------|
| `list_labels`  | Список лейблов        |
| `add_labels`   | Добавить лейблы       |
| `remove_label` | Удалить лейбл         |

### Вложения (3)

| Инструмент          | Описание                  |
|---------------------|---------------------------|
| `list_attachments`  | Список вложений           |
| `upload_attachment` | Загрузить файл (base64)   |
| `delete_attachment` | Удалить вложение          |

---

## Установка

```bash
git clone <repo-url> confluence-mcp
cd confluence-mcp
uv venv && source .venv/bin/activate
uv pip install -e .
```

## API-токен Atlassian

1. Откройте https://id.atlassian.com/manage-profile/security/api-tokens
2. Нажмите **Create API token**
3. Скопируйте токен

## Настройка KIRO

Добавьте в `.kiro/settings/mcp.json` в корне вашего проекта:

```json
{
  "mcpServers": {
    "confluence": {
      "command": "uv",
      "args": ["run", "/абсолютный/путь/к/confluence-mcp/server.py"],
      "env": {
        "CONFLUENCE_URL": "https://your-domain.atlassian.net/wiki",
        "CONFLUENCE_USERNAME": "your-email@example.com",
        "CONFLUENCE_API_TOKEN": "your-token"
      }
    }
  }
}
```

Перезапустите KIRO после редактирования конфига. Инструменты Confluence появятся в списке доступных.

---

## Примеры использования в KIRO

### Создать страницу

```
Создай страницу "API Reference" в space ENG с таблицей эндпоинтов
```

KIRO вызовет: `create_page(space_key="ENG", title="API Reference", body_html="<table>...")`

### Обновить маленькую страницу

```
Обнови страницу 12345 — поменяй заголовок на "Updated Guide" и исправь опечатку в первом абзаце
```

KIRO вызовет: `get_page("12345")` → `update_page("12345", ...)`

### Редактировать большую страницу (>50 КБ) — без нагрузки на контекст

```
Добавь секцию "Monitoring" в конец страницы 12345
```

KIRO вызовет: `append_to_page("12345", "<h2>Monitoring</h2><p>...</p>")`

Полное тело страницы **никогда не попадает в контекстное окно**.

```
Замени секцию "Deployment" на странице 12345 обновлёнными шагами
```

KIRO вызовет: `replace_section_in_page("12345", "Deployment", "<h2>Deployment</h2><p>новые шаги...</p>")`

### Поиск на иврите

```
Найди все страницы в DEV space где упоминается "תיעוד"
```

KIRO вызовет: `search_pages('type=page AND space=DEV AND text~"תיעוד"')`

---

## Как работают инструменты для больших страниц

Традиционный подход (проблема с большими страницами):

```
get_page(id)         →  80 КБ HTML в контекст
  ... LLM обрабатывает ...
update_page(id, ...) →  80 КБ HTML обратно в API
```

С инструментами для больших страниц:

```
append_to_page(id, "<h2>Новый раздел</h2><p>...</p>")
```

В контексте только ~200 байт. Сервер делает остальное.

| Сценарий                       | Инструмент               | Использование контекста |
|--------------------------------|--------------------------|------------------------|
| Добавить контент в конец       | `append_to_page`         | Только дельта          |
| Добавить контент в начало      | `prepend_to_page`        | Только дельта          |
| Исправить опечатку / слово     | `replace_in_page`        | Строки поиска/замены   |
| Переписать одну секцию         | `replace_section_in_page`| Только новая секция    |
| Превью перед редактированием   | `get_page_excerpt`       | Макс. 2 КБ            |
| Узнать номер версии            | `get_page_metadata`      | ~500 байт              |

---

## Confluence Storage Format

Тело страниц и комментариев использует Confluence Storage Format (XHTML-подобный). Иврит работает нативно:

```html
<p>English paragraph</p>
<p dir="rtl">פסקה בעברית</p>
<h2>Заголовок</h2>
<ul>
  <li>Пункт один</li>
  <li dir="rtl">פריט בעברית</li>
</ul>
<ac:structured-macro ac:name="code">
  <ac:parameter ac:name="language">python</ac:parameter>
  <ac:plain-text-body><![CDATA[print("hello")]]></ac:plain-text-body>
</ac:structured-macro>
```

> **Совет:** Добавляйте `dir="rtl"` к элементам с текстом на иврите для правильного отображения справа налево.

---

## Шпаргалка по CQL (для `search_pages`)

```
type=page AND space=DEV
type=page AND title="Deployment Guide"
type=page AND title~"deploy"
type=page AND text~"API"
type=page AND label="docs"
type=page AND creator=currentUser()
type=page AND lastModified > "2025-01-01"
type=page AND space=DEV ORDER BY lastModified DESC
```
