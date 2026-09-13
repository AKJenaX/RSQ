import { useState, useEffect } from 'react';
import { collection, onSnapshot, Timestamp } from 'firebase/firestore';
import { db } from '../firebase/config';
import type { Donation, Fund, Allocation, Expense, FinancialReport } from '../types/finance';
import { isFirebaseConfigured } from '../firebase/config';

export type FinanceLoadState = 'loading' | 'success' | 'error' | 'permission-denied' | 'empty' | 'not-configured';

export function useFinance() {
  const [donations, setDonations] = useState<Donation[]>([]);
  const [funds, setFunds] = useState<Fund[]>([]);
  const [allocations, setAllocations] = useState<Allocation[]>([]);
  const [expenses, setExpenses] = useState<Expense[]>([]);
  const [financialReports, setFinancialReports] = useState<FinancialReport[]>([]);
  const [loadState, setLoadState] = useState<FinanceLoadState>('loading');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isFirebaseConfigured()) {
      setLoadState('not-configured');
      return;
    }

    const unsubs: (() => void)[] = [];
    
    try {
      let loaded = { d: false, f: false, a: false, e: false, r: false };
      const checkLoaded = () => {
        if (loaded.d && loaded.f && loaded.a && loaded.e && loaded.r) {
          setLoadState('success');
        }
      };

      const toMilliseconds = (value: unknown): number => {
        if (typeof value === 'number' && Number.isFinite(value)) return value < 1e10 ? value * 1000 : value;
        if (value instanceof Timestamp) return value.toMillis();
        if (value && typeof value === 'object' && typeof (value as { seconds?: unknown }).seconds === 'number') {
          return (value as { seconds: number }).seconds * 1000;
        }
        return 0;
      };

      const byCreatedAt = <T extends { createdAt?: unknown }>(items: T[]) =>
        items.sort((a, b) => toMilliseconds(b.createdAt) - toMilliseconds(a.createdAt));

      const normalizeDonation = (id: string, value: Record<string, unknown>): Donation => {
        const isOnline = !!(value.paymentId || value.orderId);
        return {
          ...value,
          id,
          donationId: typeof value.donationId === 'string' ? value.donationId : id,
          donorType: typeof value.donorType === 'string' ? value.donorType.toUpperCase() as Donation['donorType'] : 'ANONYMOUS',
          donationType: isOnline ? 'ONLINE' : (typeof value.donationType === 'string' ? value.donationType.toUpperCase() as Donation['donationType'] : 'CASH'),
          status: typeof value.status === 'string' ? value.status.toUpperCase() as Donation['status'] : 'PENDING',
          amount: typeof value.amount === 'number' ? value.amount : 0,
          createdAt: toMilliseconds(value.createdAt ?? value.timestamp),
          orderId: typeof value.orderId === 'string' ? value.orderId : undefined,
          paymentId: typeof value.paymentId === 'string' ? value.paymentId : undefined,
          userId: typeof value.userId === 'string' ? value.userId : undefined,
          date: typeof value.date === 'string' ? value.date : undefined,
        };
      };
      const normalizeFund = (id: string, value: Record<string, unknown>): Fund => ({
        ...value,
        id,
        fundId: typeof value.fundId === 'string' ? value.fundId : id,
        status: typeof value.status === 'string' ? value.status.toUpperCase() as Fund['status'] : 'ACTIVE',
        totalAmount: typeof value.totalAmount === 'number' ? value.totalAmount : 0,
        allocatedAmount: typeof value.allocatedAmount === 'number' ? value.allocatedAmount : 0,
        utilizedAmount: typeof value.utilizedAmount === 'number' ? value.utilizedAmount : 0,
        remainingAmount: typeof value.remainingAmount === 'number' ? value.remainingAmount : 0,
        purpose: typeof value.purpose === 'string' ? value.purpose : 'Untitled fund',
        source: typeof value.source === 'string' ? value.source : 'Not specified',
        createdAt: toMilliseconds(value.createdAt),
        createdBy: typeof value.createdBy === 'string' ? value.createdBy : '',
      });
      const normalizeAllocation = (id: string, value: Record<string, unknown>): Allocation => ({
        ...value,
        id,
        allocationId: typeof value.allocationId === 'string' ? value.allocationId : id,
        fundId: typeof value.fundId === 'string' ? value.fundId : '',
        category: typeof value.category === 'string' ? value.category.toUpperCase() as Allocation['category'] : 'OTHER',
        amount: typeof value.amount === 'number' ? value.amount : 0,
        reason: typeof value.reason === 'string' ? value.reason : '',
        status: typeof value.status === 'string' ? value.status.toUpperCase() as Allocation['status'] : 'PENDING',
        requestedBy: typeof value.requestedBy === 'string' ? value.requestedBy : '',
        createdAt: toMilliseconds(value.createdAt),
      });
      const normalizeExpense = (id: string, value: Record<string, unknown>): Expense => ({
        ...value,
        id,
        expenseId: typeof value.expenseId === 'string' ? value.expenseId : id,
        category: typeof value.category === 'string' ? value.category.toUpperCase() as Expense['category'] : 'OTHER',
        amount: typeof value.amount === 'number' ? value.amount : 0,
        description: typeof value.description === 'string' ? value.description : '',
        status: typeof value.status === 'string' ? value.status.toUpperCase() as Expense['status'] : 'PENDING',
        createdAt: toMilliseconds(value.createdAt),
        createdBy: typeof value.createdBy === 'string' ? value.createdBy : '',
        approvedAt: toMilliseconds(value.approvedAt),
        paidAt: toMilliseconds(value.paidAt),
      });
      const normalizeFinancialReport = (id: string, value: Record<string, unknown>): FinancialReport => ({
        ...value,
        id,
        reportId: typeof value.reportId === 'string' ? value.reportId : id,
        name: typeof value.name === 'string' ? value.name : '',
        period: typeof value.period === 'string' ? value.period : '',
        status: 'GENERATED',
        generatedAt: toMilliseconds(value.generatedAt),
        generatedBy: typeof value.generatedBy === 'string' ? value.generatedBy : '',
        snapshot: value.snapshot as FinancialReport['snapshot'] || { income: 0, allocated: 0, expenditure: 0, netPosition: 0, fundCount: 0, fundBalances: [] },
      });

      // Do not use orderBy here: it excludes legacy documents without createdAt.
      // All collections remain live via onSnapshot and are sorted after normalisation.
      const handleError = (err: any) => {
        if (err.code === 'permission-denied') {
          setLoadState('permission-denied');
          setError('Permission Denied');
        } else {
          setLoadState('error');
          setError(err.message);
        }
      };

      unsubs.push(onSnapshot(collection(db, 'donations'), (snap) => {
        setDonations(byCreatedAt(snap.docs.map(d => normalizeDonation(d.id, d.data()))));
        loaded.d = true;
        checkLoaded();
      }, (err) => { handleError(err); loaded.d = true; checkLoaded(); }));

      unsubs.push(onSnapshot(collection(db, 'funds'), (snap) => {
        setFunds(byCreatedAt(snap.docs.map(d => normalizeFund(d.id, d.data()))));
        loaded.f = true;
        checkLoaded();
      }, (err) => { handleError(err); loaded.f = true; checkLoaded(); }));

      unsubs.push(onSnapshot(collection(db, 'allocations'), (snap) => {
        setAllocations(byCreatedAt(snap.docs.map(d => normalizeAllocation(d.id, d.data()))));
        loaded.a = true;
        checkLoaded();
      }, (err) => { handleError(err); loaded.a = true; checkLoaded(); }));

      unsubs.push(onSnapshot(collection(db, 'expenses'), (snap) => {
        setExpenses(byCreatedAt(snap.docs.map(d => normalizeExpense(d.id, d.data()))));
        loaded.e = true;
        checkLoaded();
      }, (err) => { handleError(err); loaded.e = true; checkLoaded(); }));
      
      unsubs.push(onSnapshot(collection(db, 'financialReports'), (snap) => {
        const reps = snap.docs.map(d => normalizeFinancialReport(d.id, d.data())).sort((a, b) => b.generatedAt - a.generatedAt);
        setFinancialReports(reps);
        loaded.r = true;
        checkLoaded();
      }, (err) => { handleError(err); loaded.r = true; checkLoaded(); }));
      
    } catch (err: any) {
      if (err.code === 'permission-denied') setLoadState('permission-denied');
      else setLoadState('error');
      setError(err.message);
    }

    return () => {
      unsubs.forEach(unsub => unsub());
    };
  }, []);

  return { donations, funds, allocations, expenses, financialReports, loadState, error, loading: loadState === 'loading' };
}
