import {
  collection,
  doc,
  runTransaction,
  Transaction
} from 'firebase/firestore';
import { db } from '../firebase/config';
import type { Donation, Fund, Allocation, Expense, FinancialReport } from '../types/finance';

function logTxFinancialActivity(transaction: Transaction, type: string, entityId: string, userId: string, metadata: any) {
  const ref = doc(collection(db, 'activity'));
  transaction.set(ref, {
    id: ref.id,
    type,
    entityId,
    timestamp: Date.now(),
    performedBy: userId,
    metadata
  });
}

export async function createDonation(donation: Omit<Donation, 'donationId' | 'createdAt' | 'status'>, userId: string): Promise<string> {
  const ref = doc(collection(db, 'donations'));
  const data: Donation = {
    ...donation,
    donationId: ref.id,
    status: 'PENDING',
    createdAt: Date.now(),
  };
  
  await runTransaction(db, async (transaction) => {
    transaction.set(ref, data);
    logTxFinancialActivity(transaction, 'DONATION_CREATED', ref.id, userId, { amount: data.amount, donationType: data.donationType });
  });
  return ref.id;
}

export async function verifyDonation(donationId: string, userId: string): Promise<void> {
  await runTransaction(db, async (transaction) => {
    const ref = doc(db, 'donations', donationId);
    const snap = await transaction.get(ref);
    if (!snap.exists()) throw new Error("Donation not found");
    if (snap.data().status !== 'PENDING') throw new Error("Only PENDING donations can be verified");
    
    transaction.update(ref, {
      status: 'VERIFIED',
      verifiedAt: Date.now(),
      verifiedBy: userId
    });
    logTxFinancialActivity(transaction, 'DONATION_VERIFIED', donationId, userId, {});
  });
}

export async function rejectDonation(donationId: string, userId: string): Promise<void> {
  await runTransaction(db, async (transaction) => {
    const ref = doc(db, 'donations', donationId);
    const snap = await transaction.get(ref);
    if (!snap.exists()) throw new Error("Donation not found");
    if (snap.data().status !== 'PENDING') throw new Error("Only PENDING donations can be rejected");
    
    transaction.update(ref, {
      status: 'REJECTED'
    });
    logTxFinancialActivity(transaction, 'DONATION_REJECTED', donationId, userId, {});
  });
}

export async function createFund(fund: Omit<Fund, 'fundId' | 'createdAt' | 'createdBy' | 'status' | 'allocatedAmount' | 'utilizedAmount' | 'remainingAmount'>, userId: string): Promise<string> {
  const ref = doc(collection(db, 'funds'));
  const data: Fund = {
    ...fund,
    fundId: ref.id,
    allocatedAmount: 0,
    utilizedAmount: 0,
    remainingAmount: fund.totalAmount,
    status: 'ACTIVE',
    createdAt: Date.now(),
    createdBy: userId
  };
  await runTransaction(db, async (transaction) => {
    transaction.set(ref, data);
    logTxFinancialActivity(transaction, 'FUND_CREATED', ref.id, userId, { amount: fund.totalAmount });
  });
  return ref.id;
}

export async function allocateFund(fundId: string, allocation: Omit<Allocation, 'allocationId' | 'fundId' | 'status' | 'requestedBy' | 'createdAt' | 'approvedBy' | 'approvedAt' | 'utilizedAmount' | 'remainingAmount'>, userId: string): Promise<string> {
  return await runTransaction(db, async (transaction) => {
    const fundRef = doc(db, 'funds', fundId);
    const fundSnap = await transaction.get(fundRef);
    if (!fundSnap.exists()) throw new Error("Fund not found");
    
    const fundData = fundSnap.data() as Fund;
    if (fundData.remainingAmount < allocation.amount) {
      throw new Error("Insufficient remaining amount in fund");
    }

    const allocRef = doc(collection(db, 'allocations'));
    const allocData: Allocation = {
      ...allocation,
      allocationId: allocRef.id,
      fundId,
      status: 'PENDING',
      requestedBy: userId,
      createdAt: Date.now()
    };
    
    transaction.set(allocRef, allocData);
    logTxFinancialActivity(transaction, 'ALLOCATION_CREATED', allocRef.id, userId, { amount: allocation.amount });
    return allocRef.id;
  });
}

export async function approveAllocation(allocationId: string, userId: string): Promise<void> {
  await runTransaction(db, async (transaction) => {
    const allocRef = doc(db, 'allocations', allocationId);
    const allocSnap = await transaction.get(allocRef);
    if (!allocSnap.exists()) throw new Error("Allocation not found");
    const allocData = allocSnap.data() as Allocation;
    
    if (allocData.status !== 'PENDING') throw new Error("Only PENDING allocations can be approved");
    if (allocData.amount <= 0) throw new Error("Invalid allocation amount");
    
    const fundRef = doc(db, 'funds', allocData.fundId);
    const fundSnap = await transaction.get(fundRef);
    if (!fundSnap.exists()) throw new Error("Fund not found");
    const fundData = fundSnap.data() as Fund;
    
    if (fundData.remainingAmount < allocData.amount) {
      throw new Error("Insufficient remaining amount in fund");
    }
    if (fundData.allocatedAmount + allocData.amount > fundData.totalAmount) {
      throw new Error("Fund total amount exceeded");
    }
    
    transaction.update(allocRef, {
      status: 'APPROVED',
      approvedBy: userId,
      approvedAt: Date.now(),
      utilizedAmount: 0,
      remainingAmount: allocData.amount
    });
    
    transaction.update(fundRef, {
      allocatedAmount: fundData.allocatedAmount + allocData.amount,
      remainingAmount: fundData.remainingAmount - allocData.amount
    });
    
    logTxFinancialActivity(transaction, 'ALLOCATION_APPROVED', allocationId, userId, { amount: allocData.amount });
  });
}

export async function rejectAllocation(allocationId: string, userId: string): Promise<void> {
  await runTransaction(db, async (transaction) => {
    const allocRef = doc(db, 'allocations', allocationId);
    const allocSnap = await transaction.get(allocRef);
    if (!allocSnap.exists()) throw new Error("Allocation not found");
    const allocData = allocSnap.data() as Allocation;
    
    if (allocData.status !== 'PENDING') throw new Error("Only PENDING allocations can be rejected");
    
    transaction.update(allocRef, {
      status: 'REJECTED'
    });
    logTxFinancialActivity(transaction, 'ALLOCATION_REJECTED', allocationId, userId, {});
  });
}

export async function createExpense(expense: Omit<Expense, 'expenseId' | 'status' | 'createdAt' | 'createdBy' | 'approvedBy' | 'approvedAt' | 'paidAt' | 'paidBy'>, userId: string): Promise<string> {
  const ref = doc(collection(db, 'expenses'));
  const data: Expense = {
    ...expense,
    expenseId: ref.id,
    status: 'PENDING',
    createdAt: Date.now(),
    createdBy: userId
  };
  await runTransaction(db, async (transaction) => {
    if (expense.fundId) {
      const fundRef = doc(db, 'funds', expense.fundId);
      const fundSnap = await transaction.get(fundRef);
      if (!fundSnap.exists()) throw new Error("Fund not found");
      const fundData = fundSnap.data() as Fund;
      
      const availableToSpend = fundData.allocatedAmount - fundData.utilizedAmount;
      if (expense.amount > availableToSpend) {
        throw new Error("Expense exceeds the available balance of this fund.");
      }
    }

    transaction.set(ref, data);
    logTxFinancialActivity(transaction, 'EXPENSE_CREATED', ref.id, userId, { amount: expense.amount });
  });
  return ref.id;
}

export async function approveExpense(expenseId: string, userId: string): Promise<void> {
  await runTransaction(db, async (transaction) => {
    const ref = doc(db, 'expenses', expenseId);
    const snap = await transaction.get(ref);
    if (!snap.exists()) throw new Error("Expense not found");
    if (snap.data().status !== 'PENDING') throw new Error("Only PENDING expenses can be approved");
    
    transaction.update(ref, {
      status: 'APPROVED',
      approvedBy: userId,
      approvedAt: Date.now()
    });
    logTxFinancialActivity(transaction, 'EXPENSE_APPROVED', expenseId, userId, {});
  });
}

export async function rejectExpense(expenseId: string, userId: string): Promise<void> {
  await runTransaction(db, async (transaction) => {
    const ref = doc(db, 'expenses', expenseId);
    const snap = await transaction.get(ref);
    if (!snap.exists()) throw new Error("Expense not found");
    if (snap.data().status !== 'PENDING') throw new Error("Only PENDING expenses can be rejected");
    
    transaction.update(ref, {
      status: 'REJECTED'
    });
    logTxFinancialActivity(transaction, 'EXPENSE_REJECTED', expenseId, userId, {});
  });
}

export async function payExpense(expenseId: string, userId: string): Promise<void> {
  return await runTransaction(db, async (transaction) => {
    const expRef = doc(db, 'expenses', expenseId);
    const expSnap = await transaction.get(expRef);
    if (!expSnap.exists()) throw new Error("Expense not found");
    const expData = expSnap.data() as Expense;

    if (expData.status !== 'APPROVED') {
      throw new Error("Only APPROVED expenses can be paid");
    }
    if (expData.amount <= 0) {
      throw new Error("Invalid expense amount");
    }
    if (!expData.fundId) {
      throw new Error("Expense must be associated with a fundId to be paid");
    }

    const fundRef = doc(db, 'funds', expData.fundId);
    const fundSnap = await transaction.get(fundRef);
    if (!fundSnap.exists()) throw new Error("Fund not found");
    const fundData = fundSnap.data() as Fund;
    
    if (fundData.utilizedAmount + expData.amount > fundData.allocatedAmount) {
      throw new Error("Insufficient allocated balance in fund for this expense");
    }
    
    const newUtilizedAmount = fundData.utilizedAmount + expData.amount;

    if (expData.allocationId) {
       const allocRef = doc(db, 'allocations', expData.allocationId);
       const allocSnap = await transaction.get(allocRef);
       if (!allocSnap.exists()) throw new Error("Allocation not found");
       
       const allocData = allocSnap.data() as Allocation;
       if (allocData.fundId !== expData.fundId) throw new Error("Allocation does not belong to specified fund");
       if (allocData.status !== 'APPROVED') throw new Error("Allocation is not APPROVED");
       if (allocData.remainingAmount === undefined || allocData.utilizedAmount === undefined) throw new Error("Allocation missing utilization fields");
       if (allocData.remainingAmount < expData.amount) throw new Error("Insufficient remaining amount in allocation");
       
       const newAllocationUtilized = allocData.utilizedAmount + expData.amount;
       const newAllocationRemaining = allocData.amount - newAllocationUtilized;
       
       transaction.update(allocRef, {
         utilizedAmount: newAllocationUtilized,
         remainingAmount: newAllocationRemaining,
         status: newAllocationRemaining === 0 ? 'UTILIZED' : 'APPROVED'
       });
    }

    transaction.update(fundRef, {
      utilizedAmount: newUtilizedAmount
    });
    
    transaction.update(expRef, {
      status: 'PAID',
      paidAt: Date.now(),
      paidBy: userId
    });

    logTxFinancialActivity(transaction, 'EXPENSE_PAID', expenseId, userId, { amount: expData.amount, fundId: expData.fundId });
  });
}

export async function generateFinancialReport(name: string, period: string, userId: string, snapshot: FinancialReport['snapshot']): Promise<string> {
  const ref = doc(collection(db, 'financialReports'));
  const data: FinancialReport = {
    reportId: ref.id,
    name,
    period,
    status: 'GENERATED',
    generatedAt: Date.now(),
    generatedBy: userId,
    snapshot
  };
  
  await runTransaction(db, async (transaction) => {
    transaction.set(ref, data);
    logTxFinancialActivity(transaction, 'REPORT_GENERATED', ref.id, userId, { period });
  });
  
  return ref.id;
}