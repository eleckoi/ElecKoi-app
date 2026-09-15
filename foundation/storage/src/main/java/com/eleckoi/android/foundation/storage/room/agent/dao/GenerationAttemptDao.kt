package com.eleckoi.android.foundation.storage.room.agent.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.eleckoi.android.foundation.storage.room.agent.entity.GenerationAttemptEntity

@Dao
interface GenerationAttemptDao {
    @Insert
    fun insert(attempt: GenerationAttemptEntity)

    @Query("SELECT * FROM generation_attempts WHERE id = :attemptId LIMIT 1")
    fun byId(attemptId: String): GenerationAttemptEntity?

    @Query(
        "SELECT * FROM generation_attempts " +
            "WHERE conversationId = :conversationId AND kind = :kind AND ownerId = :ownerId " +
            "ORDER BY attemptNumber DESC LIMIT 1",
    )
    fun latest(
        conversationId: String,
        kind: String,
        ownerId: String,
    ): GenerationAttemptEntity?

    @Query(
        "SELECT * FROM generation_attempts " +
            "WHERE conversationId = :conversationId AND kind = 'reply' " +
            "AND outputMessageId = :messageId " +
            "ORDER BY createdAtMillis DESC LIMIT 1",
    )
    fun latestReplyForMessage(
        conversationId: String,
        messageId: String,
    ): GenerationAttemptEntity?

    @Query(
        "SELECT * FROM generation_attempts WHERE conversationId = :conversationId " +
            "AND state IN ('queued', 'running')",
    )
    fun liveForConversation(conversationId: String): List<GenerationAttemptEntity>

    @Query(
        "SELECT * FROM generation_attempts WHERE conversationId = :conversationId " +
            "AND (ownerId IN (:messageIds) OR outputMessageId IN (:messageIds))",
    )
    fun forMessages(conversationId: String, messageIds: List<String>): List<GenerationAttemptEntity>

    @Query(
        "DELETE FROM generation_attempts WHERE conversationId = :conversationId " +
            "AND (ownerId IN (:messageIds) OR outputMessageId IN (:messageIds))",
    )
    fun deleteForMessages(conversationId: String, messageIds: List<String>)

    @Query("SELECT * FROM generation_attempts WHERE parentAttemptId = :parentAttemptId")
    fun children(parentAttemptId: String): List<GenerationAttemptEntity>

    @Query(
        "UPDATE generation_attempts SET state = 'running', startedAtMillis = :startedAtMillis " +
            "WHERE id = :attemptId AND state = 'queued' AND supersededByAttemptId IS NULL",
    )
    fun start(
        attemptId: String,
        startedAtMillis: Long,
    ): Int

    @Query(
        "UPDATE generation_attempts SET " +
            "state = CASE WHEN state IN ('queued', 'running') THEN 'superseded' ELSE state END, " +
            "finishedAtMillis = CASE WHEN state IN ('queued', 'running') " +
            "THEN :finishedAtMillis ELSE finishedAtMillis END, " +
            "supersededByAttemptId = :newAttemptId " +
            "WHERE id = :attemptId AND supersededByAttemptId IS NULL",
    )
    fun supersede(
        attemptId: String,
        newAttemptId: String,
        finishedAtMillis: Long,
    ): Int

    @Query(
        "UPDATE generation_attempts SET " +
            "state = CASE WHEN state IN ('queued', 'running') THEN 'superseded' ELSE state END, " +
            "finishedAtMillis = CASE WHEN state IN ('queued', 'running') " +
            "THEN :finishedAtMillis ELSE finishedAtMillis END, " +
            "supersededByAttemptId = :newAttemptId " +
            "WHERE parentAttemptId = :parentAttemptId AND supersededByAttemptId IS NULL",
    )
    fun supersedeChildren(
        parentAttemptId: String,
        newAttemptId: String,
        finishedAtMillis: Long,
    ): Int

    @Query(
        "UPDATE generation_attempts SET state = :state, finishedAtMillis = :finishedAtMillis, " +
            "errorMessage = :errorMessage, outputPath = :outputPath " +
            "WHERE id = :attemptId AND state IN ('queued', 'running')",
    )
    fun finish(
        attemptId: String,
        state: String,
        finishedAtMillis: Long,
        errorMessage: String,
        outputPath: String,
    ): Int
}
