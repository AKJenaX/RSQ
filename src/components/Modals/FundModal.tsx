import React, { useState } from 'react';
import { createFund } from '../../services/financeService';
import { useAuth } from '../../hooks/useAuth';

interface FundModalProps {
  onClose: () => void;
}

export function FundModal({ onClose }: FundModalProps) {
  const { user } = useAuth();
  const [purpose, setPurpose] = useState('');
  const [source, setSource] = useState('RSQ Global Fund');
  const [totalAmount, setTotalAmount] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    const amount = Number(totalAmount);
    if (!amount || amount <= 0) {
      setError('Please enter a valid budget amount.');
      return;
    }
    if (!purpose.trim()) {
      setError('Please enter a fund name/purpose.');
      return;
    }

    setIsSubmitting(true);
    try {
      if (!user) throw new Error('Not authenticated');
      await createFund({
        source: source.trim(),
        totalAmount: amount,
        purpose: purpose.trim(),
      }, user.uid);
      onClose();
    } catch (err: any) {
      console.error(err);
      setError(err.message || 'Failed to create fund.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-background/80 p-4 backdrop-blur-sm">
      <div className="w-full max-w-md rounded-xl border border-border bg-card p-6 shadow-lg">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-semibold">Create Fund / Programme</h2>
          <button onClick={onClose} className="text-muted-foreground hover:text-foreground">✕</button>
        </div>

        {error && (
          <div className="mb-4 rounded-md border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="mb-1 block text-sm font-medium">Fund Name / Purpose</label>
            <input
              type="text"
              value={purpose}
              onChange={e => setPurpose(e.target.value)}
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:border-ring"
              placeholder="e.g. Emergency Medical Relief"
              required
            />
          </div>

          <div>
            <label className="mb-1 block text-sm font-medium">Source</label>
            <input
              type="text"
              value={source}
              onChange={e => setSource(e.target.value)}
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:border-ring"
              placeholder="e.g. General Donations"
              required
            />
          </div>

          <div>
            <label className="mb-1 block text-sm font-medium">Budget ($)</label>
            <input
              type="number"
              min="0"
              step="0.01"
              value={totalAmount}
              onChange={e => setTotalAmount(e.target.value)}
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:border-ring"
              placeholder="0.00"
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
              {isSubmitting ? 'Creating...' : 'Create Fund'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
