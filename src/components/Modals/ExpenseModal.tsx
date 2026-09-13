import { useState, useMemo } from 'react';
import { X } from 'lucide-react';
import { createExpense } from '../../services/financeService';
import type { Fund } from '../../types/finance';

interface ExpenseModalProps {
  authorityUid: string;
  funds: Fund[];
  onClose: () => void;
}

const categories = ['MEDICAL', 'FOOD', 'WATER', 'SHELTER', 'TRANSPORTATION', 'RESCUE', 'EQUIPMENT', 'OTHER'] as const;

export function ExpenseModal({ authorityUid, funds, onClose }: ExpenseModalProps) {
  const [category, setCategory] = useState<(typeof categories)[number]>('OTHER');
  const [amount, setAmount] = useState('');
  const [vendor, setVendor] = useState('');
  const [description, setDescription] = useState('');
  const [fundId, setFundId] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const selectedFund = useMemo(() => funds.find(f => (f.id ?? f.fundId) === fundId), [funds, fundId]);
  const availableToSpend = selectedFund ? selectedFund.allocatedAmount - selectedFund.utilizedAmount : null;
  const parsedAmount = Number(amount);
  const isZeroBalance = availableToSpend !== null && availableToSpend <= 0;
  const isAmountTooHigh = availableToSpend !== null && Number.isFinite(parsedAmount) && parsedAmount > availableToSpend;
  
  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    const parsedAmount = Number(amount);
    if (!Number.isFinite(parsedAmount) || parsedAmount <= 0 || !description.trim()) {
      setError('Enter a positive amount and a description.');
      return;
    }
    
    if (availableToSpend !== null && parsedAmount > availableToSpend) {
      setError(`Expense exceeds the available balance of $${availableToSpend.toLocaleString()}.`);
      return;
    }

    setSaving(true);
    setError(null);
    try {
      await createExpense({ category, amount: parsedAmount, vendor: vendor.trim() || undefined, description: description.trim(), fundId: fundId || undefined }, authorityUid);
      onClose();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to record the expense.');
      setSaving(false);
    }
  };

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true" aria-labelledby="expense-modal-title">
      <form className="modal-content" onSubmit={submit}>
        <div className="modal-header"><h2 id="expense-modal-title" className="modal-title">Record expense</h2><button type="button" className="modal-close" onClick={onClose} aria-label="Close dialog" disabled={saving}><X size={20} /></button></div>
        <div className="modal-body grid gap-4">
          {error ? <div className="form-error" role="alert">{error}</div> : null}
          {isZeroBalance ? (
            <div className="rounded-md border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-sm text-amber-600">
              This fund has no allocated balance available for expenses. Allocate funds to this programme before recording an expense.
            </div>
          ) : null}
          <div className="grid grid-cols-2 gap-3">
            <div className="form-group"><label className="form-label" htmlFor="expense-category">Category</label><select id="expense-category" className="form-input" value={category} onChange={(event) => setCategory(event.target.value as typeof category)} disabled={saving}>{categories.map((item) => <option key={item}>{item}</option>)}</select></div>
            <div className="form-group"><label className="form-label" htmlFor="expense-amount">Amount</label><input id="expense-amount" className="form-input" inputMode="decimal" value={amount} onChange={(event) => setAmount(event.target.value)} disabled={saving} required />
              {isAmountTooHigh && !isZeroBalance && (
                <p className="mt-1 text-xs text-destructive">
                  Expense exceeds the available balance of ${availableToSpend?.toLocaleString()}.
                </p>
              )}
            </div>
          </div>
          <div className="form-group"><label className="form-label" htmlFor="expense-vendor">Vendor</label><input id="expense-vendor" className="form-input" value={vendor} onChange={(event) => setVendor(event.target.value)} disabled={saving} /></div>
          <div className="form-group"><label className="form-label" htmlFor="expense-description">Description</label><textarea id="expense-description" className="form-input" rows={3} value={description} onChange={(event) => setDescription(event.target.value)} disabled={saving} required /></div>
          <div className="form-group">
            <label className="form-label" htmlFor="expense-fund">Fund (optional)</label>
            <select id="expense-fund" className="form-input" value={fundId} onChange={(event) => setFundId(event.target.value)} disabled={saving}>
              <option value="">Not assigned to a fund</option>
              {funds.map((fund) => {
                const fundAvailable = fund.allocatedAmount - fund.utilizedAmount;
                const isUnavailable = fundAvailable <= 0;
                const text = isUnavailable 
                  ? `${fund.purpose || fund.fundId} — Available: $0 (Unavailable)`
                  : `${fund.purpose || fund.fundId} — Available: $${fundAvailable.toLocaleString()}`;
                
                return (
                  <option key={fund.id ?? fund.fundId} value={fund.id ?? fund.fundId} disabled={isUnavailable}>
                    {text}
                  </option>
                );
              })}
            </select>
            {availableToSpend !== null && !isZeroBalance && (
              <p className="mt-1 text-xs text-muted-foreground">
                Available to spend: ${availableToSpend.toLocaleString()}
              </p>
            )}
          </div>
        </div>
        <div className="modal-footer"><button type="button" className="btn-secondary" onClick={onClose} disabled={saving}>Cancel</button><button className="btn-primary" disabled={saving || isAmountTooHigh || isZeroBalance}>{saving ? 'Saving…' : 'Record expense'}</button></div>
      </form>
    </div>
  );
}
