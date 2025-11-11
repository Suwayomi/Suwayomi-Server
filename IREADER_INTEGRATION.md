# IReader Integration for Suwayomi-Server

This document describes the IReader extension support that has been added to Suwayomi-Server, allowing it to load and run IReader extensions alongside Tachiyomi extensions.

## Overview

Suwayomi-Server now supports both Tachiyomi and IReader extensions simultaneously. IReader is a novel/light novel reader app with its own extension ecosystem. This integration allows users to access novel sources through the same server infrastructure.

## Architecture

The IReader integration follows the same pattern as Tachiyomi extensions:

### Core Components

1. **IReader Source API** (`server/src/main/kotlin/ireader/`)
   - Copied from IReader's source-api module
   - Contains interfaces: `Source`, `CatalogSource`, `HttpSource`
   - Model classes: `MangaInfo`, `ChapterInfo`, `Page`, etc.

2. **Database Tables**
   - `IReaderExtensionTable`: Stores IReader extension metadata
   - `IReaderSourceTable`: Stores IReader source information
   - `IReaderSourceMetaTable`: Stores source-specific metadata

3. **Extension Management** (`suwayomi/tachidesk/manga/impl/extension/ireader/`)
   - `IReaderExtension.kt`: Handles installation, uninstallation, and updates
   - `IReaderExtensionsList.kt`: Lists available IReader extensions

4. **Source Management** (`suwayomi/tachidesk/manga/impl/`)
   - `IReaderSource.kt`: Manages IReader source instances and caching

5. **API Controllers** (`suwayomi/tachidesk/manga/controller/`)
   - `IReaderExtensionController.kt`: REST API for extension operations
   - `IReaderSourceController.kt`: REST API for source operations

## API Endpoints

All IReader endpoints are prefixed with `/api/v1/ireader/`

### Extension Endpoints

- `GET /api/v1/ireader/extension/list` - List all IReader extensions
- `GET /api/v1/ireader/extension/install/{pkgName}` - Install an extension by package name
- `POST /api/v1/ireader/extension/install` - Upload and install an APK file
- `GET /api/v1/ireader/extension/update/{pkgName}` - Update an extension
- `GET /api/v1/ireader/extension/uninstall/{pkgName}` - Uninstall an extension
- `GET /api/v1/ireader/extension/icon/{apkName}` - Get extension icon

### Source Endpoints

- `GET /api/v1/ireader/extension/source/list` - List all IReader sources
- `GET /api/v1/ireader/source/{sourceId}` - Get specific source details

## Installation

IReader extensions are stored separately from Tachiyomi extensions:
- Extensions directory: `{dataRoot}/extensions/ireader/`
- Icons directory: `{dataRoot}/extensions/ireader/icon/`

## Extension Format

IReader extensions must be APK files with:
- Feature flag: `ireader.extension`
- Metadata: `ireader.extension.class` (main class name)
- Metadata: `ireader.extension.nsfw` (optional, "1" for NSFW content)

## Key Differences from Tachiyomi

1. **Content Type**: IReader focuses on novels/light novels (text content) while Tachiyomi focuses on manga (image content)
2. **Page Model**: IReader uses a more flexible `Page` sealed class that supports:
   - `Text`: Plain text content
   - `ImageUrl`: Image URLs
   - `MovieUrl`: Video URLs
   - `PageUrl`: Lazy-loaded pages
3. **Commands**: IReader sources support a command system for optimizing server requests
4. **Listings**: IReader uses a `Listing` system for different content categories

## Usage Example

### Installing an IReader Extension

```bash
# Upload an APK file
curl -X POST http://localhost:4567/api/v1/ireader/extension/install \
  -F "file=@my-ireader-extension.apk"
```

### Listing IReader Sources

```bash
curl http://localhost:4567/api/v1/ireader/source/list
```

## Future Enhancements

Potential improvements for the IReader integration:

1. **Content Browsing**: Add endpoints for browsing novels from IReader sources
2. **Search**: Implement search functionality across IReader sources
3. **Reading**: Add endpoints for fetching and displaying novel chapters
4. **Library Management**: Support adding IReader novels to the library
5. **Tracking**: Integrate with novel tracking services
6. **Downloads**: Support downloading novel chapters for offline reading
7. **Extension Repository**: Add support for an IReader extension repository

## Technical Notes

- IReader extensions are loaded using the same JAR loading mechanism as Tachiyomi
- Source instances are cached to avoid repeated class loading
- The integration maintains complete separation from Tachiyomi functionality
- Both systems can coexist without conflicts

## Migration

The database migration `M0053_IReaderTables` creates the necessary tables automatically on server startup.

## Compatibility

- Supports IReader extension library versions 1.0 to 2.0
- Compatible with existing Suwayomi-Server infrastructure
- No changes required to existing Tachiyomi functionality
