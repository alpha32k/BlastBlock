package com.blastblock.game.domain.repository;

import com.blastblock.game.domain.model.ScoreRecord;

import java.io.IOException;
import java.util.List;

/** Stores completed games and reads the leaderboard one page at a time. */
public interface ScoreHistoryRepository {

    /** Highest score first, then oldest completion, then insertion order. */
    List<ScoreRecord> getScores(long offset, int limit) throws IOException;

    /**
     * Always inserts the completed game.
     * @return position if it merits a rank banner, or 0 otherwise
     */
    int addScore(ScoreRecord record) throws IOException;
}
