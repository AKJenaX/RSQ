import React, { useState } from 'react';
import { allocateFund, approveAllocation } from '../../services/financeService';
import { useAuth } from '../../hooks/useAuth';
import type { Fund } from '../../types/finance';

interface AllocationModalProps {
  funds: Fund[];
  onClose: () => void;
}

const ALLOCATION_CATEGORIES = [
  'MEDICAL', 'FOOD', 'WATER', 'SHELTER', 'TRANSPORTATION', 'RESCUE', 'EQUIPMENT', 'OTHER'
];

export function AllocationModal({ funds, onClose }: AllocationModalProps) {
  const { user } = useAuth();
  const [fundId, setFundId] = useState(funds[0]?.fundId || '');
  const [category, setCategory] = useState(ALLOCATION_CATEGORIES[0]);
  const [amount, setAmount] = useState('');
  const [reason, setReason] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const selectedFund = funds.find(f => f.fundId === fundId);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    const numAmount = Number(amount);
    if (!numAmount || numAmount <= 0) {
      setError('Please enter a valid allocation amount greater than 0.');
      return;
    }
    
    if (selectedFund && numAmount > selectedFund.remainingAmount) {
      setError(`Amount exceeds the fund's remaining balance of $${selectedFund.remainingAmount.toLocaleString()}`);
      return;
    }

    if (!reason.trim()) {
      setError('Please provide a description/reason.');
      return;
    }

    setIsSubmitting(true);
    try {
      if (!user) throw new Error('Not authenticated');
      
      // Create the allocation
      const allocationId = await allocateFund(fundId, {
        category: category as any,
        amount: numAmount,
        reason: reason.trim()
      }, user.uid);
      
      // Auto-approve so it immediately reflects in the budget
      await approveAllocation(allocationId, user.uid);
      
      onClose();
    } catch (err: any) {
      console.error(err);
      setError(err.message || 'Failed to create allocation.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-background/80 p-4 backdrop-blur-sm">
      <div className="w-full max-w-md rounded-xl border border-border bg-card p-6 shadow-lg">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-semibold">Create Allocation</h2>
          <button onClick={onClose} className="text-muted-foreground hover:text-foreground">✕</button>
        </div>

        {error && (
          <div className="mb-4 rounded-md border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive">
            {error}
          </div>
        )}

        {funds.length === 0 ? (
          <div className="text-sm text-muted-foreground">
            No active funds available. Please create a fund first.
            <div className="mt-4 flex justify-end">
              <button onClick={onClose} className="rounded-md px-4 py-2 text-sm font-medium hover:bg-accent">Close</button>
            </div>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="mb-1 block text-sm font-medium">Source Fund</label>
              <select
                value={fundId}
                onChange={e => setFundId(e.target.value)}
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:border-ring"
                required
              >
                {funds.map(f => (
                  <option key={f.fundId} value={f.fundId}>
                    {f.purpose || f.fundId} (Bal: ${f.remainingAmount.toLocaleString()})
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="mb-1 block text-sm font-medium">Destination / Programme</label>
              <select
                value={category}
                onChange={e => setCategory(e.target.value)}
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:border-ring"
                required
              >
                {ALLOCATION_CATEGORIES.map(c => (
                  <option key={c} value={c}>{c}</option>
                ))}
              </select>
            </div>

            <div>
              <label className="mb-1 block text-sm font-medium">Amount ($)</label>
              <input
                type="number"
                min="0.01"
                step="0.01"
                max={selectedFund?.remainingAmount}
                value={amount}
                onChange={e => setAmount(e.target.value)}
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:border-ring"
                placeholder="0.00"
                required
              />
              {selectedFund && (
                <p className="mt-1 text-xs text-muted-foreground">
                  Available: ${selectedFund.remainingAmount.toLocaleString()}
                </p>
              )}
            </div>
            
            <div>
              <label className="mb-1 block text-sm font-medium">Description</label>
              <input
                type="text"
                value={reason}
                onChange={e => setReason(e.target.value)}
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:border-ring"
                placeholder="e.g. Zone A Distribution"
                required
              />
            </div>

            <div className="mt-6 flex justify-end gap-3">
              <button
                type="button"
                onClick={onClose}
                disabled={isSubmitting}
                className="rounded-md px-4 py-2 text-sm font-medium hover:bg-accent"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={isSubmitting}
                className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
              >
                {isSubmitting ? 'Allocating...' : 'Allocate Funds'}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}
