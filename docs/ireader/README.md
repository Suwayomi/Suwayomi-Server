# IReader Extension Support

Tachidesk-Server now supports IReader extensions, allowing you to browse and read light novels from various sources.

## What Was Added

### 1. IReader Source API Integration
- Copied all source-api classes from IReader-master to provide the runtime classes that extensions expect
- Location: `server/src/main/kotlin/ireader/core/source/`
- Includes: Source, CatalogSource, HttpSource, and all model classes (MangaInfo, ChapterInfo, Page, etc.)

### 2. Runtime Dependencies
Added all dependencies that IReader extensions require:
- Ktor HTTP Client (v3.1.2) - For network requests
- Ktor Serialization - For JSON/data handling
- Kotlinx Serialization (v1.8.0) - For data serialization

### 3. Extension Loading System
- Extensions are loaded with the server's classloader as parent
- Extensions find all required classes in the server's classpath
- No ClassNotFoundException or NoClassDefFoundError

### 4. API Endpoints
Complete REST API for managing extensions and browsing novels.

## API Endpoints

### Extension Management

#### List Extensions
```http
GET /api/v1/ireader/extension/list
```
Returns all available IReader extensions.

#### Install Extension
```http
GET /api/v1/ireader/extension/install/{pkgName}
```
Example:
```bash
curl "http://localhost:4567/api/v1/ireader/extension/install/ireader.freewebnovel.en"
```

#### Upload Extension
```http
POST /api/v1/ireader/extension/install
Content-Type: multipart/form-data
```

#### Update Extension
```http
GET /api/v1/ireader/extension/update/{pkgName}
```

**Note:** You must call `/api/v1/ireader/extension/list` first to detect available updates before calling update.

Example:
```bash
# 1. Refresh extension list to detect updates
curl "http://localhost:4567/api/v1/ireader/extension/list"

# 2. Update the extension
curl "http://localhost:4567/api/v1/ireader/extension/update/ireader.freewebnovel.en"
```

#### Uninstall Extension
```http
GET /api/v1/ireader/extension/uninstall/{pkgName}
```

#### Get Extension Icon
```http
GET /api/v1/ireader/extension/icon/{apkName}
```

### Source Management

#### List Sources
```http
GET /api/v1/ireader/source/list
```
Returns all installed IReader sources.

**Response:**
```json
[
  {
    "id": "1234567890",
    "name": "FreeWebNovel",
    "lang": "en",
    "iconUrl": "/api/v1/ireader/extension/icon/ireader.freewebnovel.en.apk",
    "supportsLatest": true,
    "isConfigurable": false,
    "isNsfw": false,
    "displayName": "FreeWebNovel",
    "baseUrl": null
  }
]
```

#### Get Source Details
```http
GET /api/v1/ireader/source/{sourceId}
```

### Novel Browsing

#### Browse Popular Novels
```http
GET /api/v1/ireader/source/{sourceId}/popular/{page}
```

**Example:**
```bash
curl "http://localhost:4567/api/v1/ireader/source/1234567890/popular/1"
```

**Response:**
```json
{
  "mangas": [
    {
      "key": "https://freewebnovel.com/novel-123",
      "title": "Cultivation Chat Group",
      "cover": "https://freewebnovel.com/cover.jpg",
      "author": "",
      "description": "",
      "genres": [],
      "status": 0
    }
  ],
  "hasNextPage": true
}
```

#### Browse Latest Novels
```http
GET /api/v1/ireader/source/{sourceId}/latest/{page}
```

#### Search Novels
```http
GET /api/v1/ireader/source/{sourceId}/search?query={query}&page={page}
```

**Example:**
```bash
curl "http://localhost:4567/api/v1/ireader/source/1234567890/search?query=cultivation&page=1"
```

### Novel Details

#### Get Novel Details
```http
GET /api/v1/ireader/source/{sourceId}/details?novelUrl={encodedUrl}
```

**Example:**
```bash
# URL must be encoded
curl "http://localhost:4567/api/v1/ireader/source/1234567890/details?novelUrl=https%3A%2F%2Ffreewebnovel.com%2Fnovel-123"
```

**Response:**
```json
{
  "key": "https://freewebnovel.com/novel-123",
  "title": "Cultivation Chat Group",
  "author": "Legend of the Paladin",
  "description": "On a certain day, Song Shuhang accidentally joined...",
  "genres": ["Action", "Adventure", "Comedy", "Fantasy"],
  "status": 1,
  "cover": "https://freewebnovel.com/cover.jpg"
}
```

**Status Values:**
- `0` = Unknown
- `1` = Ongoing
- `2` = Completed
- `3` = Licensed
- `4` = Publishing Finished
- `5` = Cancelled
- `6` = On Hiatus

#### Get Chapter List
```http
GET /api/v1/ireader/source/{sourceId}/chapters?novelUrl={encodedUrl}
```

**Response:**
```json
[
  {
    "key": "https://freewebnovel.com/chapter-1",
    "name": "Chapter 1: Accidentally Joined a Cultivation Chat Group",
    "dateUpload": 1234567890000,
    "number": 1.0,
    "scanlator": "",
    "type": 1
  }
]
```

#### Get Chapter Content
```http
GET /api/v1/ireader/source/{sourceId}/chapter?chapterUrl={encodedUrl}
```

**Response:**
```json
[
  {
    "text": "Chapter 1: Accidentally Joined a Cultivation Chat Group"
  },
  {
    "text": "On a certain day, Song Shuhang accidentally joined a deeply afflicted Xianxia chuunibyou..."
  },
  {
    "text": "Another paragraph of the chapter..."
  }
]
```

## Complete Example: Reading a Novel

### Step 1: Install Extension
```bash
curl "http://localhost:4567/api/v1/ireader/extension/install/ireader.freewebnovel.en"
```

### Step 2: Get Source ID
```bash
curl "http://localhost:4567/api/v1/ireader/source/list" | jq '.[0].id'
# Output: "1234567890"
```

### Step 3: Browse Popular Novels
```bash
curl "http://localhost:4567/api/v1/ireader/source/1234567890/popular/1" | jq '.mangas[0]'
```

**Output:**
```json
{
  "key": "https://freewebnovel.com/cultivation-chat-group.html",
  "title": "Cultivation Chat Group",
  "cover": "https://freewebnovel.com/files/article/image/0/13/13.jpg"
}
```

### Step 4: Get Novel Details
```bash
# URL encode the novel key
NOVEL_URL="https://freewebnovel.com/cultivation-chat-group.html"
ENCODED_URL=$(echo -n "$NOVEL_URL" | jq -sRr @uri)

curl "http://localhost:4567/api/v1/ireader/source/1234567890/details?novelUrl=$ENCODED_URL" | jq
```

**Output:**
```json
{
  "key": "https://freewebnovel.com/cultivation-chat-group.html",
  "title": "Cultivation Chat Group",
  "author": "Legend of the Paladin",
  "description": "On a certain day, Song Shuhang accidentally joined...",
  "genres": ["Action", "Adventure", "Comedy", "Fantasy", "Martial Arts", "Xianxia"],
  "status": 2,
  "cover": "https://freewebnovel.com/files/article/image/0/13/13.jpg"
}
```

### Step 5: Get Chapter List
```bash
curl "http://localhost:4567/api/v1/ireader/source/1234567890/chapters?novelUrl=$ENCODED_URL" | jq '.[0]'
```

**Output:**
```json
{
  "key": "https://freewebnovel.com/cultivation-chat-group/chapter-1.html",
  "name": "Chapter 1: Accidentally Joined a Cultivation Chat Group",
  "dateUpload": 0,
  "number": 1.0,
  "scanlator": "",
  "type": 1
}
```

### Step 6: Read Chapter Content
```bash
CHAPTER_URL="https://freewebnovel.com/cultivation-chat-group/chapter-1.html"
ENCODED_CHAPTER=$(echo -n "$CHAPTER_URL" | jq -sRr @uri)

curl "http://localhost:4567/api/v1/ireader/source/1234567890/chapter?chapterUrl=$ENCODED_CHAPTER" | jq '.[0:3]'
```

**Output:**
```json
[
  {
    "text": "Chapter 1: Accidentally Joined a Cultivation Chat Group"
  },
  {
    "text": "On a certain day, Song Shuhang accidentally joined a deeply afflicted Xianxia chuunibyou..."
  },
  {
    "text": "The entire group shared the common goal of ascending to immortality..."
  }
]
```

## PowerShell Example

```powershell
# Install extension
Invoke-RestMethod -Uri "http://localhost:4567/api/v1/ireader/extension/install/ireader.freewebnovel.en"

# Get sources
$sources = Invoke-RestMethod -Uri "http://localhost:4567/api/v1/ireader/source/list"
$sourceId = $sources[0].id

# Browse popular
$popular = Invoke-RestMethod -Uri "http://localhost:4567/api/v1/ireader/source/$sourceId/popular/1"
$novel = $popular.mangas[0]

# Get details
$encodedUrl = [System.Web.HttpUtility]::UrlEncode($novel.key)
$details = Invoke-RestMethod -Uri "http://localhost:4567/api/v1/ireader/source/$sourceId/details?novelUrl=$encodedUrl"

# Get chapters
$chapters = Invoke-RestMethod -Uri "http://localhost:4567/api/v1/ireader/source/$sourceId/chapters?novelUrl=$encodedUrl"

# Read first chapter
$chapterUrl = [System.Web.HttpUtility]::UrlEncode($chapters[0].key)
$content = Invoke-RestMethod -Uri "http://localhost:4567/api/v1/ireader/source/$sourceId/chapter?chapterUrl=$chapterUrl"

# Display
Write-Host "Novel: $($details.title)" -ForegroundColor Cyan
Write-Host "Author: $($details.author)" -ForegroundColor Gray
Write-Host "Chapters: $($chapters.Count)" -ForegroundColor Gray
Write-Host "`nFirst Chapter:" -ForegroundColor Yellow
$content[0..2] | ForEach-Object { Write-Host $_.text }
```

## JavaScript/TypeScript Example

```javascript
const BASE_URL = 'http://localhost:4567/api/v1';

// Install extension
await fetch(`${BASE_URL}/ireader/extension/install/ireader.freewebnovel.en`);

// Get sources
const sources = await fetch(`${BASE_URL}/ireader/source/list`).then(r => r.json());
const sourceId = sources[0].id;

// Browse popular
const popular = await fetch(`${BASE_URL}/ireader/source/${sourceId}/popular/1`).then(r => r.json());
const novel = popular.mangas[0];

// Get details
const novelUrl = encodeURIComponent(novel.key);
const details = await fetch(`${BASE_URL}/ireader/source/${sourceId}/details?novelUrl=${novelUrl}`).then(r => r.json());

// Get chapters
const chapters = await fetch(`${BASE_URL}/ireader/source/${sourceId}/chapters?novelUrl=${novelUrl}`).then(r => r.json());

// Read chapter
const chapterUrl = encodeURIComponent(chapters[0].key);
const content = await fetch(`${BASE_URL}/ireader/source/${sourceId}/chapter?chapterUrl=${chapterUrl}`).then(r => r.json());

console.log('Novel:', details.title);
console.log('Chapters:', chapters.length);
console.log('Content:', content.map(p => p.text).join('\n\n'));
```

## Python Example

```python
import requests
from urllib.parse import quote

BASE_URL = 'http://localhost:4567/api/v1'

# Install extension
requests.get(f'{BASE_URL}/ireader/extension/install/ireader.freewebnovel.en')

# Get sources
sources = requests.get(f'{BASE_URL}/ireader/source/list').json()
source_id = sources[0]['id']

# Browse popular
popular = requests.get(f'{BASE_URL}/ireader/source/{source_id}/popular/1').json()
novel = popular['mangas'][0]

# Get details
novel_url = quote(novel['key'])
details = requests.get(f'{BASE_URL}/ireader/source/{source_id}/details?novelUrl={novel_url}').json()

# Get chapters
chapters = requests.get(f'{BASE_URL}/ireader/source/{source_id}/chapters?novelUrl={novel_url}').json()

# Read chapter
chapter_url = quote(chapters[0]['key'])
content = requests.get(f'{BASE_URL}/ireader/source/{source_id}/chapter?chapterUrl={chapter_url}').json()

print(f"Novel: {details['title']}")
print(f"Author: {details['author']}")
print(f"Chapters: {len(chapters)}")
print("\nFirst Chapter:")
for paragraph in content[:3]:
    print(paragraph['text'])
```

## Available Extensions

Popular IReader extensions you can install:
- `ireader.freewebnovel.en` - FreeWebNovel
- `ireader.bestlightnovel.en` - BestLightNovel
- `ireader.wuxiaworld.en` - WuxiaWorld
- `ireader.novelupdates.en` - NovelUpdates
- `ireader.readlightnovel.en` - ReadLightNovel
- `ireader.webnovel.en` - WebNovel

## Notes

- **URL Encoding**: Always URL-encode novel and chapter URLs when passing them as query parameters
- **Pagination**: Page numbers start at 1
- **Rate Limiting**: Some sources may have rate limits, implement delays if needed
- **Content Format**: Chapter content is returned as an array of text paragraphs
- **Images**: Image support in chapters is not yet implemented
- **NSFW**: Some sources may contain NSFW content (check `isNsfw` field)

## Troubleshooting

### Extension Won't Install
- Check server logs for errors
- Ensure the extension package name is correct
- Verify internet connection for downloading

### Source Returns Empty Results
- The source website may be down
- The source may require authentication (not yet supported)
- Try a different source

### Chapter Content is Empty
- The source may have changed its HTML structure
- The extension may need an update
- Check if the chapter URL is valid

## Architecture

```
┌─────────────────────────────────────┐
│  IReader Extension (JAR)            │
│  - Source implementation only       │
│  - No dependencies bundled          │
└─────────────────────────────────────┘
                ↓ loaded by
┌─────────────────────────────────────┐
│  URLClassLoader                     │
│  (parent: server classloader)       │
└─────────────────────────────────────┘
                ↓ finds classes in
┌─────────────────────────────────────┐
│  Server Classpath                   │
│  - ireader.core.source.*            │
│  - Ktor HTTP Client                 │
│  - kotlinx.serialization            │
│  - All model classes                │
└─────────────────────────────────────┘
```

## Credits

- IReader: https://github.com/IReaderorg/IReader
- IReader Extensions: https://github.com/IReaderorg/IReader-extensions
- Tachidesk-Server: https://github.com/Suwayomi/Suwayomi-Server
