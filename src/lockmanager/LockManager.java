package lockmanager;

import common.Trace;
import transactionmanager.Transaction;

import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public class LockManager {
    /**
     * Maps row IDs to locks.
     */
    private final Map<String, Map<Integer, Lock>> lockMap;

    /**
     * Set of finished transactions.
     */
    private final Set<Integer> finishedTransactions;

    public LockManager() {
        lockMap = new Hashtable<String, Map<Integer, Lock>>();
        finishedTransactions = new HashSet<Integer>();
    }

    public synchronized void markFinished(int transaction) {
        finishedTransactions.add(transaction);
    }

    public synchronized Lock lock(
            String datumName,
            int transaction,
            LockType lockType) {
        Map<Integer, Lock> transactionMap = lockMap.get(datumName);
        final Lock lock = new Lock(datumName, transaction, lockType);

        Trace.info(String.format(
                    "Trying to lock item %s for transaction %d",
                    datumName,
                    transaction));

        if(transactionMap == null) {
            Trace.info(String.format(
                        "Creating transaction map: %s -> %d -> Lock",
                        datumName,
                        transaction));

            transactionMap = new Hashtable<Integer, Lock>();
            lockMap.put(datumName, transactionMap);
        }

        if(lockType == LockType.LOCK_WRITE) {
            while(!canAcquireWrite(transaction, transactionMap)) {
                try {
                    wait();
                }
                catch(InterruptedException e) {
                    throw new InvalidLockException(
                            String.format(
                                "Lock request for %s to %d interrupted.",
                                datumName,
                                transaction
                            )
                    );
                }
            }

            transactionMap.put(transaction, lock);

            Trace.info(String.format(
                        "Grant WRITE lock for item %s to transaction %d",
                        datumName,
                        transaction));

            return lock;

        }
        else if(lockType == LockType.LOCK_READ) {
            while(!canAcquireRead(transaction, transactionMap)) {
                try {
                    wait();
                }
                catch(InterruptedException e) {
                    throw new InvalidLockException(
                            String.format(
                                "Lock request for item %s to transaction " +
                                "%d interrupted.",
                                datumName,
                                transaction
                            )
                    );
                }
            }

            if(transactionMap.get(transaction) == null) {
                transactionMap.put(transaction, lock);

                Trace.info(String.format(
                            "Grant READ lock for item %s to transaction %d",
                            datumName,
                            transaction));
            }
            else
                Trace.info(String.format(
                            "Using existing lock for item %s of " +
                            "transaction %d",
                            datumName,
                            transaction));

            return lock;
        }

        throw new InvalidLockException(
                String.format(
                    "Lock request for item %s to transaction %d reached " +
                    "impossible state.",
                    datumName,
                    transaction
                )
        );
    }

    public synchronized void releaseTransaction(int transaction) {
        // remove all locks owned by the transaction
        for(final Map<Integer, Lock> transactionMap : lockMap.values())
            transactionMap.remove(transaction);
        // mark the transaction as finished to cause any blocked threads to
        // abort.
        markFinished(transaction);
        notifyAll();
    }

    private synchronized boolean canAcquireRead(
            final Integer transaction,
            Map<Integer, Lock> transactionMap)
    {
        if(finishedTransactions.contains(transaction))
            throw new InvalidLockException(
                    String.format(
                        "No lock can be granted to finished transaction %d.",
                        transaction
                    )
            );

        for(final Lock lock : transactionMap.values())
            if(lock.lockType == LockType.LOCK_WRITE)
                return lock.getTransaction() == transaction;

        return true;
    }

    private synchronized boolean canAcquireWrite(
            final Integer transaction,
            Map<Integer, Lock> transactionMap) {
        if(finishedTransactions.contains(transaction))
            throw new InvalidLockException(
                    String.format(
                        "No lock can be granted to finished transaction %d.",
                        transaction
                    )
            );

        for(final Lock lock : transactionMap.values())
            if(lock.getTransaction() == transaction)
                continue;
            else
                return false;


        return true;

    }
}