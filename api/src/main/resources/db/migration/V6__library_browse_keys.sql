CREATE ALIAS NB_BROWSE_KEY DETERMINISTIC FOR 'dev.normalbias.api.BrowseOrder.sortKey';
ALTER TABLE tracks ADD title_sort VARCHAR AS (NB_BROWSE_KEY(title));
ALTER TABLE tracks ADD artist_sort VARCHAR AS (NB_BROWSE_KEY(artist));
ALTER TABLE tracks ADD album_sort VARCHAR AS (NB_BROWSE_KEY(album));
CREATE INDEX tracks_title_browse ON tracks(title_sort, title, id);
CREATE INDEX tracks_artist_browse ON tracks(artist_sort, artist, id);
CREATE INDEX tracks_album_browse ON tracks(album_sort, album, artist, id);
