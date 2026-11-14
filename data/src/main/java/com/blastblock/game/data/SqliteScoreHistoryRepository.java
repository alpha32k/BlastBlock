package com.blastblock.game.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.AtomicFile;
import android.util.Log;

import com.blastblock.game.domain.model.ScoreRecord;
import com.blastblock.game.domain.repository.ScoreHistoryRepository;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Stores each completed game as an indexed row. The legacy file is imported on database creation. */
public final class SqliteScoreHistoryRepository implements ScoreHistoryRepository {
    private static final String TABLE = "scores";
    private static final String ORDER = "score DESC, finished_at ASC, _id ASC";
    private static final int RANK_BANNER_LIMIT = 10;
    private final Database helper;

    public SqliteScoreHistoryRepository(Context context) {
        helper = new Database(context.getApplicationContext());
    }

    @Override
    public List<ScoreRecord> getScores(long offset, int limit) throws IOException {
        if (offset < 0 || limit <= 0) throw new IllegalArgumentException("Invalid score page");
        try (Cursor rows = helper.getReadableDatabase().query(TABLE,
                new String[]{"score", "finished_at", "daily"}, null, null, null, null,
                ORDER, offset + ", " + limit)) {
            List<ScoreRecord> scores = new ArrayList<>();
            while (rows.moveToNext()) {
                scores.add(new ScoreRecord(rows.getLong(0), rows.getLong(1), rows.getInt(2) != 0));
            }
            return scores;
        } catch (SQLiteException e) {
            throw new IOException("Could not read score history", e);
        }
    }

    @Override
    public int addScore(ScoreRecord record) throws IOException {
        try {
            SQLiteDatabase database = helper.getWritableDatabase();
            long id = database.insertOrThrow(TABLE, null, values(record));
            try (Cursor leaders = database.query(TABLE, new String[]{"_id"}, null,
                    null, null, null, ORDER, Integer.toString(RANK_BANNER_LIMIT))) {
                int rank = 0;
                while (leaders.moveToNext()) {
                    rank++;
                    if (leaders.getLong(0) == id) return rank;
                }
                return 0;
            }
        } catch (SQLiteException e) {
            throw new IOException("Could not save a completed game", e);
        }
    }

    private static ContentValues values(ScoreRecord record) {
        ContentValues values = new ContentValues();
        values.put("score", record.getScore());
        values.put("finished_at", record.getFinishedAtMillis());
        values.put("daily", record.isDaily() ? 1 : 0);
        return values;
    }

    private static final class Database extends SQLiteOpenHelper {
        private final AtomicFile legacy;

        Database(Context context) {
            super(context, "score_history.db", null, 1);
            legacy = new AtomicFile(new File(context.getFilesDir(), "top_scores.json"));
        }

        @Override
        public void onCreate(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE scores (_id INTEGER PRIMARY KEY, score INTEGER NOT NULL,"
                    + " finished_at INTEGER NOT NULL, daily INTEGER NOT NULL)");
            database.execSQL("CREATE INDEX score_order ON scores (" + ORDER + ")");
            for (ScoreRecord record : readLegacy()) {
                database.insertOrThrow(TABLE, null, values(record));
            }
        }

        private List<ScoreRecord> readLegacy() {
            try {
                byte[] bytes = legacy.readFully();
                return new ScoreHistoryJsonCodec().decode(new String(bytes, StandardCharsets.UTF_8));
            } catch (FileNotFoundException e) {
                return Collections.emptyList();
            } catch (InvalidSaveException e) {
                Log.w("ScoreHistory", "The legacy score file is unreadable", e);
                return Collections.emptyList();
            } catch (IOException e) {
                throw new SQLiteException("Could not import legacy score history", e);
            }
        }

        @Override
        public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
            throw new IllegalStateException("Unsupported score database version: " + newVersion);
        }
    }
}
