import React from 'react';
import { ServerCrash, Database, ShieldCheck, HardDrive } from 'lucide-react';
import { useAuth } from '../hooks/useAuth';
import { isFirebaseConfigured } from '../firebase/config';
import { useReports } from '../hooks/useReports';

export function SystemHealthPage(): React.ReactElement {
  const { user, authError } = useAuth();
  const { loadState } = useReports();
  
  const isHealthy = isFirebaseConfigured();
  
  let dbStatus = 'Connecting...';
  let dbColor = 'text-warning';
  let dbBg = 'bg-warning-subtle';
  if (loadState === 'success') { dbStatus = 'Operational'; dbColor = 'text-success'; dbBg = 'bg-success-subtle'; }
  else if (loadState === 'permission-denied') { dbStatus = 'Permission Denied'; dbColor = 'text-error'; dbBg = 'bg-error-subtle'; }
  else if (loadState === 'error') { dbStatus = 'Unavailable'; dbColor = 'text-error'; dbBg = 'bg-error-subtle'; }
  else if (loadState === 'not-configured') { dbStatus = 'Not Configured'; dbColor = 'text-error'; dbBg = 'bg-error-subtle'; }
  
  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Auth */}
        <div className="card flex items-center gap-4 p-4">
          <div className={`p-3 rounded-full ${user ? 'bg-success-subtle text-success' : (authError ? 'bg-error-subtle text-error' : 'bg-warning-subtle text-warning')}`}>
            <ShieldCheck size={24} />
          </div>
          <div>
            <div className="font-bold text-primary">Authentication</div>
            <div className={`text-sm ${user ? 'text-success' : (authError ? 'text-error' : 'text-warning')}`}>
              {user ? 'Operational' : (authError ? 'Permission Denied' : 'Configured')}
            </div>
          </div>
        </div>
        
        {/* Firestore */}
        <div className="card flex items-center gap-4 p-4">
          <div className={`p-3 rounded-full ${dbBg} ${dbColor}`}>
            <Database size={24} />
          </div>
          <div>
            <div className="font-bold text-primary">Firestore Database</div>
            <div className={`text-sm ${dbColor}`}>
              {dbStatus}
            </div>
          </div>
        </div>
        
        {/* Storage */}
        <div className="card flex items-center gap-4 p-4">
          <div className={`p-3 rounded-full ${isHealthy ? 'bg-success-subtle text-success' : 'bg-error-subtle text-error'}`}>
            <HardDrive size={24} />
          </div>
          <div>
            <div className="font-bold text-primary">Cloud Storage</div>
            <div className={`text-sm ${isHealthy ? 'text-success' : 'text-error'}`}>
              {isHealthy ? 'Connected' : 'Unavailable'}
            </div>
          </div>
        </div>
        
        {/* General System */}
        <div className="card flex items-center gap-4 p-4">
          <div className={`p-3 rounded-full ${isHealthy ? 'bg-success-subtle text-success' : 'bg-error-subtle text-error'}`}>
            <ServerCrash size={24} />
          </div>
          <div>
            <div className="font-bold text-primary">Core Services</div>
            <div className={`text-sm ${isHealthy ? 'text-success' : 'text-error'}`}>
              {isHealthy ? 'Configured' : 'Not Configured'}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}