# IReader Quick Start Guide

## Overview

Suwayomi-Server now supports IReader extensions for reading novels and light novels alongside manga from Tachiyomi extensions.

## Installation

1. **Build the server**:
   ```bash
   ./gradlew :server:shadowJar
   ```

2. **Run the server**:
   ```bash
   java -jar server/build/Suwayomi-Server-*.jar
   ```

3. **The server will automatically**:
   - Create IReader database tables
   - Set up the extensions directory at `{dataRoot}/extensions/ireader/`
   - Expose IReader API endpoints

## Installing an IReader Extension

### Method 1: Upload APK File

```bash
curl -X POST http://localhost:4567/api/v1/ireader/extension/install \
  -H "Content-Type: multipart/form-data" \
  -F "file=@/path/to/your-ireader-extension.apk"
```

### Method 2: Install by Package Name (if you have a repository)

```bash
curl http://localhost:4567/api/v1/ireader/extension/install/com.example.extension
```

## Viewing Installed Extensions

```bash
curl http://localhost:4567/api/v1/ireader/extension/list | jq
```

Response:
```json
[
  {
    "repo": "https://example.com/extension.apk",
    "apkName": "extension-v1.0.0.apk",
    "iconUrl": "/api/v1/ireader/extension/icon/extension-v1.0.0.apk",
    "name": "Example Novel Source",
    "pkgName": "com.example.extension",
    "versionName": "1.0.0",
    "versionCode": 1,
    "lang": "en",
    "isNsfw": false,
    "installed": true,
    "hasUpdate": false,
    "obsolete": false
  }
]
```

## Viewing Available Sources

```bash
curl http://localhost:4567/api/v1/ireader/source/list | jq
```

Response:
```json
[
  {
    "id": "1234567890",
    "name": "Example Novels",
    "lang": "en",
    "iconUrl": "/api/v1/ireader/extension/icon/extension-v1.0.0.apk",
    "supportsLatest": true,
    "isConfigurable": false,
    "isNsfw": false,
    "displayName": "Example Novels (EN)",
    "baseUrl": "https://example.com"
  }
]
```

## Getting Source Details

```bash
curl http://localhost:4567/api/v1/ireader/source/1234567890 | jq
```

## Uninstalling an Extension

```bash
curl http://localhost:4567/api/v1/ireader/extension/uninstall/com.example.extension
```

## Directory Structure

After installation, your data directory will look like:
```
{dataRoot}/
├── extensions/
│   ├── ireader/                    # IReader extensions
│   │   ├── extension-v1.0.0.jar   # Converted extension
│   │   └── icon/                   # Extension icons
│   └── [tachiyomi extensions]      # Existing Tachiyomi extensions
├── database                         # SQLite database (includes IReader tables)
└── [other directories]
```

## API Endpoints Reference

### Extensions
- `GET /api/v1/ireader/extension/list` - List all extensions
- `POST /api/v1/ireader/extension/install` - Upload and install APK
- `GET /api/v1/ireader/extension/install/{pkgName}` - Install by package name
- `GET /api/v1/ireader/extension/update/{pkgName}` - Update extension
- `GET /api/v1/ireader/extension/uninstall/{pkgName}` - Uninstall extension
- `GET /api/v1/ireader/extension/icon/{apkName}` - Get extension icon

### Sources
- `GET /api/v1/ireader/source/list` - List all sources
- `GET /api/v1/ireader/source/{sourceId}` - Get source details

## Troubleshooting

### Extension won't install
- Check that the APK is a valid IReader extension (has `ireader.extension` feature)
- Check server logs for detailed error messages
- Ensure the extension library version is between 1.0 and 2.0

### Source not appearing
- Verify the extension installed successfully
- Check that the extension contains valid source implementations
- Restart the server if needed

### Permission errors
- Ensure the server has write permissions to the extensions directory
- Check that the data root directory is writable

## What's Next?

This integration provides the foundation for IReader support. Currently implemented:
- ✅ Extension installation and management
- ✅ Source listing and details
- ✅ Extension icons

To be implemented (future enhancements):
- ⏳ Novel browsing (popular, latest, search)
- ⏳ Chapter listing and content fetching
- ⏳ Library management for novels
- ⏳ Reading progress tracking
- ⏳ Downloads for offline reading
- ⏳ Extension repository integration

## Getting IReader Extensions

IReader extensions are APK files that can be:
1. Built from source (if you have the extension source code)
2. Downloaded from IReader extension repositories
3. Obtained from the IReader community

**Note**: Make sure you have the rights to use any extensions you install.

## Differences from Tachiyomi

| Feature | Tachiyomi | IReader |
|---------|-----------|---------|
| Content Type | Manga (images) | Novels (text) |
| Page Format | Image URLs | Text, Images, Videos |
| Primary Use | Reading manga | Reading novels/light novels |
| Extensions | `/extensions/` | `/extensions/ireader/` |
| API Prefix | `/api/v1/` | `/api/v1/ireader/` |

## Support

For issues or questions:
1. Check the server logs for error messages
2. Verify your extension is compatible
3. Ensure you're using the correct API endpoints
4. Review the full documentation in `IREADER_INTEGRATION.md`
