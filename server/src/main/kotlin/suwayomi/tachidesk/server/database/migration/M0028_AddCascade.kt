package suwayomi.tachidesk.server.database.migration

import de.neonew.exposed.migrations.helpers.SQLMigration

@Suppress("ClassName", "unused")
class M0028_AddCascade : SQLMigration() {
    override val sql: String = """
        alter table CATEGORYMANGA
            drop constraint FK_CATEGORYMANGA_CATEGORY_ID;
        
        alter table CATEGORYMANGA
            add constraint FK_CATEGORYMANGA_CATEGORY_ID
                foreign key (CATEGORY) references CATEGORY
                    on delete cascade;
        
        alter table CATEGORYMANGA
            drop constraint FK_CATEGORYMANGA_MANGA_ID;
        
        alter table CATEGORYMANGA
            add constraint FK_CATEGORYMANGA_MANGA_ID
                foreign key (MANGA) references MANGA
                    on delete cascade;
        
        alter table CHAPTER
            drop constraint FK_CHAPTER_MANGA_ID;
        
        alter table CHAPTER
            add constraint FK_CHAPTER_MANGA_ID
                foreign key (MANGA) references MANGA
                    on delete cascade;
        
        alter table PAGE
            drop constraint FK_PAGE_CHAPTER_ID;
        
        alter table PAGE
            add constraint FK_PAGE_CHAPTER_ID
                foreign key (CHAPTER) references CHAPTER
                    on delete cascade;
    """.trimIndent()
}
