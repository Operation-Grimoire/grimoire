-- SQL2 schema frozen from d4d3f883bd13e89f09f28579b0a58f228dcf960a; invented local fixture rows.
CREATE TABLE settings(name TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE TABLE books(source_id INTEGER NOT NULL, novel_url TEXT NOT NULL, metadata TEXT NOT NULL, PRIMARY KEY(source_id,novel_url));
CREATE TABLE contents(source_id INTEGER NOT NULL, novel_url TEXT NOT NULL, chapter_url TEXT NOT NULL, has_content INTEGER NOT NULL DEFAULT 0 CHECK(has_content IN (0,1)), content TEXT, content_version INTEGER, digest TEXT, revision TEXT NOT NULL, word_count INTEGER NOT NULL DEFAULT 0 CHECK(word_count>=0), receipt_attempt TEXT, active_attempt TEXT, active_owner TEXT, base_revision TEXT, PRIMARY KEY(source_id,novel_url,chapter_url), FOREIGN KEY(source_id,novel_url) REFERENCES books(source_id,novel_url) ON DELETE CASCADE);
CREATE TABLE reading_checkpoints(source_id INTEGER NOT NULL, novel_url TEXT NOT NULL, chapter_url TEXT NOT NULL, state TEXT NOT NULL, PRIMARY KEY(source_id,novel_url,chapter_url), FOREIGN KEY(source_id,novel_url,chapter_url) REFERENCES contents(source_id,novel_url,chapter_url) ON DELETE CASCADE);
INSERT INTO settings VALUES('id','frozen-sql2'),('phase','active');
INSERT INTO books VALUES(17,'/novel','{"sourceId":17,"url":"/novel","title":"A public novel","language":"EN","favorite":true,"downloaded":[],"chapters":[{"url":"/chapter","name":"First","locked":false,"number":-1,"uploadDate":0}],"position":{"chapterUrl":"/chapter","paragraph":0,"offset":17}}');
INSERT INTO contents(source_id,novel_url,chapter_url,has_content,content,content_version,digest,revision,word_count,receipt_attempt) VALUES(17,'/novel','/chapter',1,'Legacy prose<i>Legacy prose</i>',1,'0256ce92ac93894b8671e71a56216255f6052c1588d95ea85e5468625f89d3f1','frozen-generation',2,'frozen-receipt');
PRAGMA application_id=1196575053;
PRAGMA user_version=2;
