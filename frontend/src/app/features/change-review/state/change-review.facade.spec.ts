import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { ChangeReviewFacade } from './change-review.facade';
import { ChangeReviewHttpRepository } from '../data-access/change-review-http.repository';

describe('ChangeReviewFacade', () => {
  it('loads history into signal state', () => {
    const repo = {
      listReviews: () => of([{
        reviewId: 'REV-1',
        applicationName: 'app',
        changeTitle: 't',
        createdAt: 'now',
        status: 'COMPLETED',
        riskLevel: 'LOW',
        recommendation: 'GO'
      }])
    } as Partial<ChangeReviewHttpRepository>;

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        ChangeReviewFacade,
        { provide: ChangeReviewHttpRepository, useValue: repo }
      ]
    });

    const facade = TestBed.inject(ChangeReviewFacade);
    facade.loadHistory();
    expect(facade.history().length).toBe(1);
    expect(facade.history()[0].reviewId).toBe('REV-1');
  });
});
