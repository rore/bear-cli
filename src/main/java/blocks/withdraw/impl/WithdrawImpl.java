package blocks.withdraw.impl;

// CI fixture implementation to keep repo-level BEAR gate checks deterministic.
public final class WithdrawImpl {
    public void execute(Object request, Object ledgerPort) {
        ledgerPort.toString();
    }
}
