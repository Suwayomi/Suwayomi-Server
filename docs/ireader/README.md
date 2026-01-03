# IReader Extension Support

Tachidesk-Server now supports IReader extensions, allowing you to browse and read light novels from various sources.

## What Was Added

### 1. IReader Source API Integration
- Copied all source-api classes from IReader-master to provide the runtime classes that extensions expect
- Location: `server/src/main/kotlin/ireader/core/source/`
- Includes: Source, CatalogSource, HttpSource, and all model classes (MangaInfo, ChapterInfo, Page, etc.)

### 2. Runtime Dependencies
Added all dependencies that IReader extensions require:
- Ktor HTTP Client - For network requests
- Ktor Serialization - For JSON/data handling
- Kotlinx Serialization - For data serialization

### 3. Extension Loading System
- Extensions are loaded with the server's classloader as parent
- Extensions find all required classes in the server's classpath
- No ClassNotFoundException or NoClassDefFoundError

### 4. GraphQL API
Complete GraphQL API for managing extensions and browsing novels. All data is persisted to the database.

## GraphQL API

IReader functionality is accessed exclusively through the GraphQL API at `/api/graphql`.

### Extension Management

#### List Extensions
```graphql
query {
  ireaderExtensions {
    pkgName
    name
    lang
    versionCode
    versionName
    hasUpdate
    installed
    isNsfw
    iconUrl
  }
}
```

#### List Extensions with Filters
```graphql
query {
  ireaderExtensions(installed: true, lang: "en") {
    pkgName
    name
    installed
  }
}
```

#### Get Single Extension
```graphql
query {
  ireaderExtension(pkgName: "ireader.freewebnovel.en") {
    pkgName
    name
    installed
    hasUpdate
  }
}
```

#### Get Extension Statistics
```graphql
query {
  ireaderExtensionStats {
    total
    installed
    hasUpdate
    obsolete
    byLanguage {
      language
      count
    }
  }
}
```

#### Get Available Languages
```graphql
query {
  ireaderExtensionLanguages
}
```

#### Install Extension
```graphql
mutation {
  installIReaderExtension(input: { pkgName: "ireader.freewebnovel.en" }) {
    extension {
      pkgName
      name
    }
  }
}
```

#### Update Extension
```graphql
mutation {
  updateIReaderExtension(input: { pkgName: "ireader.freewebnovel.en" }) {
    extension {
      pkgName
      versionCode
    }
  }
}
```

#### Uninstall Extension
```graphql
mutation {
  uninstallIReaderExtension(input: { pkgName: "ireader.freewebnovel.en" }) {
    success
  }
}
```

### Source Management

#### List Sources
```graphql
query {
  ireaderSources {
    nodes {
      id
      name
      lang
      iconUrl
      supportsLatest
      isNsfw
    }
    totalCount
  }
}
```

#### List Sources with Filters
```graphql
query {
  ireaderSources(condition: { lang: "en" }) {
    nodes {
      id
      name
    }
  }
}
```

#### Get Single Source
```graphql
query {
  ireaderSource(id: 1234567890) {
    id
    name
    lang
    iconUrl
  }
}
```

### Novel Browsing

All novel browsing operations save data to the database, ensuring novels have proper database IDs.

#### Fetch Popular/Latest/Search Novels
```graphql
mutation {
  fetchIReaderNovels(input: {
    source: 1234567890
    type: POPULAR  # or LATEST or SEARCH
    page: 1
    query: "cultivation"  # required for SEARCH type
  }) {
    novels {
      id          # Database ID
      sourceId
      url
      title
      thumbnailUrl
      author
      description
      genre
      status
      inLibrary
    }
    hasNextPage
  }
}
```

### Chapter Management

Chapters are saved to the database with proper IDs for later reference.

#### Fetch Chapters for a Novel
```graphql
mutation {
  fetchIReaderChapters(input: {
    novelId: 123  # Use database novel ID
    # OR use source + novelUrl:
    # source: 1234567890
    # novelUrl: "https://example.com/novel"
  }) {
    chapters {
      id          # Database ID
      name
      url
      dateUpload
      chapterNumber
      scanlator
      novelId
      isRead
      isBookmarked
      lastPageRead
      sourceOrder
    }
  }
}
```

#### Fetch Chapter Content
```graphql
mutation {
  fetchIReaderChapterContent(input: {
    chapterId: 456  # Use database chapter ID
    # OR use source + chapterUrl:
    # source: 1234567890
    # chapterUrl: "https://example.com/chapter-1"
  }) {
    pages {
      text
    }
  }
}
```


## Complete Example: Reading a Novel

### Step 1: Install Extension
```graphql
mutation {
  installIReaderExtension(input: { pkgName: "ireader.freewebnovel.en" }) {
    extension { pkgName name }
  }
}
```

### Step 2: Get Source ID
```graphql
query {
  ireaderSources(condition: { lang: "en" }) {
    nodes { id name }
  }
}
```

### Step 3: Browse Popular Novels
```graphql
mutation {
  fetchIReaderNovels(input: { source: 1234567890, type: POPULAR, page: 1 }) {
    novels {
      id
      title
      author
      thumbnailUrl
    }
    hasNextPage
  }
}
```

### Step 4: Get Chapters (using novel's database ID)
```graphql
mutation {
  fetchIReaderChapters(input: { novelId: 123 }) {
    chapters {
      id
      name
      chapterNumber
    }
  }
}
```

### Step 5: Read Chapter Content (using chapter's database ID)
```graphql
mutation {
  fetchIReaderChapterContent(input: { chapterId: 456 }) {
    pages { text }
  }
}
```

## JavaScript/TypeScript Example

```typescript
const GRAPHQL_URL = 'http://localhost:4567/api/graphql';

async function graphql(query: string, variables?: Record<string, any>) {
  const response = await fetch(GRAPHQL_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ query, variables }),
  });
  return response.json();
}

// Install extension
await graphql(`
  mutation {
    installIReaderExtension(input: { pkgName: "ireader.freewebnovel.en" }) {
      extension { pkgName }
    }
  }
`);

// Get sources
const { data } = await graphql(`
  query { ireaderSources { nodes { id name } } }
`);
const sourceId = parseInt(data.ireaderSources.nodes[0].id);

// Browse popular novels (saved to database)
const novelsResult = await graphql(`
  mutation($source: Long!, $page: Int!) {
    fetchIReaderNovels(input: { source: $source, type: POPULAR, page: $page }) {
      novels { id title author }
      hasNextPage
    }
  }
`, { source: sourceId, page: 1 });

const novel = novelsResult.data.fetchIReaderNovels.novels[0];
console.log('Novel:', novel.title, '(ID:', novel.id, ')');

// Get chapters (saved to database)
const chaptersResult = await graphql(`
  mutation($novelId: Int!) {
    fetchIReaderChapters(input: { novelId: $novelId }) {
      chapters { id name chapterNumber }
    }
  }
`, { novelId: novel.id });

const chapter = chaptersResult.data.fetchIReaderChapters.chapters[0];
console.log('Chapters:', chaptersResult.data.fetchIReaderChapters.chapters.length);

// Read chapter content
const contentResult = await graphql(`
  mutation($chapterId: Int!) {
    fetchIReaderChapterContent(input: { chapterId: $chapterId }) {
      pages { text }
    }
  }
`, { chapterId: chapter.id });

console.log('Content:', contentResult.data.fetchIReaderChapterContent.pages.map(p => p.text).join('\n\n'));
```

## Python Example

```python
import requests

GRAPHQL_URL = 'http://localhost:4567/api/graphql'

def graphql(query, variables=None):
    response = requests.post(GRAPHQL_URL, json={'query': query, 'variables': variables or {}})
    return response.json()

# Install extension
graphql('''
  mutation {
    installIReaderExtension(input: { pkgName: "ireader.freewebnovel.en" }) {
      extension { pkgName }
    }
  }
''')

# Get sources
result = graphql('query { ireaderSources { nodes { id name } } }')
source_id = int(result['data']['ireaderSources']['nodes'][0]['id'])

# Browse popular novels
result = graphql('''
  mutation($source: Long!, $page: Int!) {
    fetchIReaderNovels(input: { source: $source, type: POPULAR, page: $page }) {
      novels { id title author }
      hasNextPage
    }
  }
''', {'source': source_id, 'page': 1})

novel = result['data']['fetchIReaderNovels']['novels'][0]
print(f"Novel: {novel['title']} (ID: {novel['id']})")

# Get chapters
result = graphql('''
  mutation($novelId: Int!) {
    fetchIReaderChapters(input: { novelId: $novelId }) {
      chapters { id name chapterNumber }
    }
  }
''', {'novelId': novel['id']})

chapters = result['data']['fetchIReaderChapters']['chapters']
print(f"Chapters: {len(chapters)}")

# Read chapter content
result = graphql('''
  mutation($chapterId: Int!) {
    fetchIReaderChapterContent(input: { chapterId: $chapterId }) {
      pages { text }
    }
  }
''', {'chapterId': chapters[0]['id']})

content = result['data']['fetchIReaderChapterContent']['pages']
print("\nFirst Chapter:")
for paragraph in content[:3]:
    print(paragraph['text'])
```

## Database Persistence

All novels and chapters are automatically saved to the database when fetched:

- **Novels** are stored in `IReaderNovelTable` with fields like `id`, `url`, `title`, `author`, `description`, `genre`, `status`, `thumbnailUrl`, `inLibrary`, etc.
- **Chapters** are stored in `IReaderChapterTable` with fields like `id`, `url`, `name`, `chapterNumber`, `dateUpload`, `isRead`, `isBookmarked`, `lastPageRead`, etc.

This ensures:
- Data can be referenced by database IDs
- Reading progress can be tracked
- Library management is possible
- Offline access to metadata

## Available Extensions

Popular IReader extensions you can install:
- `ireader.freewebnovel.en` - FreeWebNovel
- `ireader.bestlightnovel.en` - BestLightNovel
- `ireader.wuxiaworld.en` - WuxiaWorld
- `ireader.novelupdates.en` - NovelUpdates
- `ireader.readlightnovel.en` - ReadLightNovel
- `ireader.webnovel.en` - WebNovel

## Notes

- **GraphQL Only**: IReader functionality is only available through the GraphQL API
- **Database IDs**: Always use database IDs (`id` field) when referencing novels and chapters
- **Pagination**: Page numbers start at 1
- **Rate Limiting**: Some sources may have rate limits, implement delays if needed
- **Content Format**: Chapter content is returned as an array of text paragraphs
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
                ↓ data persisted to
┌─────────────────────────────────────┐
│  Database                           │
│  - IReaderNovelTable                │
│  - IReaderChapterTable              │
└─────────────────────────────────────┘
```

## Credits

- IReader: https://github.com/IReaderorg/IReader
- IReader Extensions: https://github.com/IReaderorg/IReader-extensions
- Tachidesk-Server: https://github.com/Suwayomi/Suwayomi-Server
