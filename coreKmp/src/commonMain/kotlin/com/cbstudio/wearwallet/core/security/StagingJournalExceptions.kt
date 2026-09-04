package com.cbstudio.wearwallet.core.security

/**
 * Staging Journal 基礎例外
 */
open class StagingJournalException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * CAS 狀態不匹配例外
 */
class JournalCasMismatchException(
    val sessionId: String,
    val expectedState: String,
    val targetState: String,
    message: String = "CAS mismatch updating staging journal for session '$sessionId': expected state '$expectedState' to transition to '$targetState'"
) : StagingJournalException(message)

/**
 * Journal 寫入或查詢失敗例外
 */
class JournalWriteException(
    val sessionId: String,
    message: String,
    cause: Throwable? = null
) : StagingJournalException(message, cause)

/**
 * 金鑰回滾失敗例外
 */
class KeyRollbackFailedException(
    val sessionId: String,
    val stagedAlias: String,
    message: String,
    cause: Throwable? = null
) : StagingJournalException(message, cause)

/**
 * 刪除狀態機基礎例外
 */
open class DeletionJournalException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * 刪除狀態機 CAS 狀態不匹配例外
 */
class DeletionCasMismatchException(
    val walletId: Long,
    val expectedState: String,
    val targetState: String,
    message: String = "CAS mismatch updating deletion journal for wallet '$walletId': expected state '$expectedState' to transition to '$targetState'"
) : DeletionJournalException(message)

/**
 * 刪除清理未完全例外
 */
class DeletionIncompleteException(
    val walletId: Long,
    message: String,
    cause: Throwable? = null
) : DeletionJournalException(message, cause)
