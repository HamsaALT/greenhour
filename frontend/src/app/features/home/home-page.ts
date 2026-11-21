import { Component, OnInit, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { Api, errorMessage } from '../../core/api';
import { APPLIANCE_KINDS, Appliance, ApplianceKind, Household, PriceArea } from '../../core/models';
import { formatDuration, formatNumber } from '../../core/format';
import { Icon } from '../../shared/icon';

// Typical values, so people don't have to look up their appliance's manual to get started.
const PRESETS: Record<ApplianceKind, { powerKw: number; runMinutes: number }> = {
  DISHWASHER: { powerKw: 1.2, runMinutes: 120 },
  WASHING_MACHINE: { powerKw: 1.0, runMinutes: 105 },
  DRYER: { powerKw: 2.5, runMinutes: 90 },
  EV_CHARGER: { powerKw: 11, runMinutes: 180 },
  HEAT_PUMP: { powerKw: 2.0, runMinutes: 60 },
  OTHER: { powerKw: 1.0, runMinutes: 60 },
};

const quarterHours = (control: { value: number }) =>
  control.value % 15 === 0 ? null : { quarterHours: true };

@Component({
  selector: 'app-home-page',
  imports: [Icon, ReactiveFormsModule, RouterLink, TranslatePipe],
  templateUrl: './home-page.html',
  styleUrl: './home-page.css',
})
export class HomePage implements OnInit {
  private readonly api = inject(Api);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly kinds = APPLIANCE_KINDS;
  protected readonly errorMessage = errorMessage;
  protected readonly formatNumber = formatNumber;
  protected readonly formatDuration = formatDuration;

  protected readonly household = signal<Household | null>(null);
  protected readonly appliances = signal<Appliance[]>([]);
  protected readonly loadError = signal<unknown>(null);

  protected readonly householdForm = this.fb.group({
    name: this.fb.control('', [Validators.required, Validators.maxLength(80)]),
    priceArea: this.fb.control<PriceArea>('DK2'),
  });
  protected readonly householdSaved = signal(false);

  protected readonly editingId = signal<number | 'new' | null>(null);
  protected readonly confirmDeleteId = signal<number | null>(null);
  protected readonly formError = signal<unknown>(null);

  protected readonly applianceForm = this.fb.group({
    name: this.fb.control('', [Validators.required, Validators.maxLength(60)]),
    kind: this.fb.control<ApplianceKind>('DISHWASHER'),
    powerKw: this.fb.control(1.2, [Validators.required, Validators.min(0.05), Validators.max(22)]),
    runMinutes: this.fb.control(120, [
      Validators.required,
      Validators.min(15),
      Validators.max(720),
      quarterHours,
    ]),
  });

  ngOnInit(): void {
    this.api.household().subscribe({
      next: (h) => {
        this.household.set(h);
        this.householdForm.setValue({ name: h.name, priceArea: h.priceArea });
      },
      error: (e) => this.loadError.set(e),
    });
    this.reloadAppliances();
  }

  protected saveHousehold(): void {
    if (this.householdForm.invalid) return;
    const { name, priceArea } = this.householdForm.getRawValue();
    this.api.updateHousehold(name, priceArea).subscribe({
      next: (h) => {
        this.household.set(h);
        this.householdForm.markAsPristine();
        this.householdSaved.set(true);
        setTimeout(() => this.householdSaved.set(false), 2500);
      },
      error: (e) => this.loadError.set(e),
    });
  }

  protected startAdd(): void {
    this.formError.set(null);
    this.applianceForm.reset({ name: '', kind: 'DISHWASHER', ...PRESETS.DISHWASHER });
    this.editingId.set('new');
  }

  protected startEdit(a: Appliance): void {
    this.formError.set(null);
    this.applianceForm.setValue({
      name: a.name,
      kind: a.kind,
      powerKw: a.powerKw,
      runMinutes: a.runMinutes,
    });
    this.editingId.set(a.id);
  }

  /** When adding, switching the type fills in typical power and duration. */
  protected kindChanged(): void {
    if (this.editingId() !== 'new') return;
    const preset = PRESETS[this.applianceForm.controls.kind.value];
    this.applianceForm.patchValue(preset);
  }

  protected cancelEdit(): void {
    this.editingId.set(null);
  }

  protected saveAppliance(): void {
    if (this.applianceForm.invalid) {
      this.applianceForm.markAllAsTouched();
      return;
    }
    const input = this.applianceForm.getRawValue();
    const id = this.editingId();
    const request =
      id === 'new' ? this.api.createAppliance(input) : this.api.updateAppliance(id!, input);

    request.subscribe({
      next: () => {
        this.editingId.set(null);
        this.reloadAppliances();
      },
      error: (e) => this.formError.set(e),
    });
  }

  protected deleteAppliance(id: number): void {
    this.api.deleteAppliance(id).subscribe({
      next: () => {
        this.confirmDeleteId.set(null);
        this.appliances.update((list) => list.filter((a) => a.id !== id));
      },
      error: (e) => this.loadError.set(e),
    });
  }

  private reloadAppliances(): void {
    this.api.appliances().subscribe({
      next: (list) => this.appliances.set(list),
      error: (e) => this.loadError.set(e),
    });
  }
}
