// One leave-balance card. Shown in the strip at the top of /time/leaves —
// one per leave type. The first card in the strip is `highlight` so it
// gets the accent-radial treatment and reads as the headline number.

import { Clock, Plane } from 'lucide-react';
import { StatCard } from '@/components';
import {
  formatDateMonthDay,
  prettyLeaveCode,
  type LeaveBalanceDto,
} from '@/attendance';

export type BalanceCardProps = {
  bal: LeaveBalanceDto;
  highlight: boolean;
};

export function BalanceCard({ bal, highlight }: BalanceCardProps) {
  const total = bal.allocated + bal.carried;
  return (
    <StatCard
      variant={highlight ? 'accent-radial' : 'plain'}
      tone={bal.available <= 1 ? 'warning' : highlight ? 'success' : 'info'}
      accentCorner="tr"
      icon={bal.leaveTypeCode === 'SICK' ? Clock : Plane}
      label={prettyLeaveCode(bal.leaveTypeCode)}
      value={`${bal.available.toFixed(1)}d`}
      delta={
        <span>
          <strong>{bal.used.toFixed(1)}d</strong> used ·{' '}
          <strong>{bal.pending.toFixed(1)}d</strong> pending · {total.toFixed(0)}d allocated
          {bal.cycleStart && bal.cycleEnd ? (
            <>
              <br />
              Cycle {formatDateMonthDay(bal.cycleStart)} – {formatDateMonthDay(bal.cycleEnd)}
            </>
          ) : null}
        </span>
      }
    />
  );
}
