package com.mtg.collection.service;

import com.mtg.collection.dto.ImportResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * In-memory state for a single async import job.
 */
public class ImportJobStatus {

    public enum State { RUNNING, DONE, ERROR }

    private final String  jobId;
    private final String  user;
    private final String  format;
    private volatile State   state       = State.RUNNING;
    private volatile int     cardsCount;
    private volatile int     addedCount;
    private volatile int     removedCount;
    private volatile int     newCardsCount;
    private volatile String  errorMessage;
    private final    Instant startedAt   = Instant.now();
    private volatile Instant finishedAt;
    private volatile List<ImportResult.DuplicateInfo>  duplicatesRemoved = new ArrayList<>();
    private volatile List<ImportResult.UnknownSetEntry> unknownSetCodes   = new ArrayList<>();

    public ImportJobStatus(String jobId, String user, String format) {
        this.jobId  = jobId;
        this.user   = user;
        this.format = format;
    }

    public void markDone(int cardsCount, int addedCount, int removedCount, int newCardsCount) {
        this.cardsCount    = cardsCount;
        this.addedCount    = addedCount;
        this.removedCount  = removedCount;
        this.newCardsCount = newCardsCount;
        this.state         = State.DONE;
        this.finishedAt    = Instant.now();
    }

    public void markDone(int cardsCount, int addedCount, int removedCount, int newCardsCount,
                         List<ImportResult.DuplicateInfo>  duplicatesRemoved,
                         List<ImportResult.UnknownSetEntry> unknownSetCodes) {
        markDone(cardsCount, addedCount, removedCount, newCardsCount);
        if (duplicatesRemoved != null) this.duplicatesRemoved = duplicatesRemoved;
        if (unknownSetCodes   != null) this.unknownSetCodes   = unknownSetCodes;
    }

    public void markError(String message) {
        this.errorMessage = message;
        this.state        = State.ERROR;
        this.finishedAt   = Instant.now();
    }

    public String  getJobId()               { return jobId;               }
    public String  getUser()                { return user;                }
    public String  getFormat()              { return format;              }
    public State   getState()               { return state;               }
    public int     getCardsCount()          { return cardsCount;          }
    public int     getAddedCount()          { return addedCount;          }
    public int     getRemovedCount()        { return removedCount;        }
    public int     getNewCardsCount()       { return newCardsCount;       }
    public String  getErrorMessage()        { return errorMessage;        }
    public Instant getStartedAt()           { return startedAt;           }
    public Instant getFinishedAt()          { return finishedAt;          }
    public List<ImportResult.DuplicateInfo>  getDuplicatesRemoved() { return duplicatesRemoved; }
    public List<ImportResult.UnknownSetEntry> getUnknownSetCodes()   { return unknownSetCodes;   }
}
