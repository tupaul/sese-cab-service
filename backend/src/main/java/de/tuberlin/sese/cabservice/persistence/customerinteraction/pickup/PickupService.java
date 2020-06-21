package de.tuberlin.sese.cabservice.persistence.customerinteraction.pickup;

import com.google.common.collect.Lists;
import de.tuberlin.sese.cabservice.persistence.cab.location.CabLocationEntity;
import de.tuberlin.sese.cabservice.persistence.cab.location.CabLocationService;
import de.tuberlin.sese.cabservice.persistence.cab.registration.CabRepo;
import de.tuberlin.sese.cabservice.persistence.job.JobEntity;
import de.tuberlin.sese.cabservice.persistence.job.JobService;
import de.tuberlin.sese.cabservice.util.exceptions.CabCustomerPositionConflict;
import de.tuberlin.sese.cabservice.util.exceptions.UnknownCabIdException;
import de.tuberlin.sese.cabservice.util.exceptions.UnknownCabLocationException;
import de.tuberlin.sese.cabservice.util.exceptions.UnknownJobIdException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

import static com.google.common.collect.Streams.stream;
import static de.tuberlin.sese.cabservice.persistence.job.JobEntity.CustomerState.IN_CAB;
import static de.tuberlin.sese.cabservice.persistence.job.JobEntity.CustomerState.WAITING;

@Service
@RequiredArgsConstructor
public class PickupService {

    private final PickupRepo repo;

    private final CabRepo cabRepo;

    private final CabLocationService locationService;

    private final JobService jobService;

    public void pickup(Long cabId, Long customerId) {
        if (cabId == null || customerId == 0) {
            throw new IllegalArgumentException("Cab ID or customer ID is null");
        }

        if (!cabRepo.findById(cabId).isPresent()) {
            throw new UnknownCabIdException();
        }

        Optional<CabLocationEntity> cabLocationOptional = locationService.getCabLocation(cabId);
        if (!cabLocationOptional.isPresent()) {
            throw new UnknownCabLocationException();
        }
        Optional<JobEntity> jobOptional = jobService.getJob(customerId);
        if (!jobOptional.isPresent()) {
            throw new UnknownJobIdException();
        }

        JobEntity job = jobOptional.get();
        Integer customerSection = job.getStart();
        Integer cabSection = cabLocationOptional.get().getSection();

        if (!cabSection.equals(customerSection)) {
            throw new CabCustomerPositionConflict();
        }

        if (!WAITING.equals(job.getCustomerState())) {
            throw new CabCustomerPositionConflict("Already picked up customer");
        }

        repo.save(PickupRequestEntity.builder()
                .customerId(customerId)
                .cabId(cabId)
                .build());
    }

    public List<PickupRequestEntity> getPickupRequests() {
        return Lists.newArrayList(repo.findAll());
    }

    public PickupCompleteModel pickupsComplete(Long cabId) {
        if (cabId == null) {
            throw new IllegalArgumentException("Cab ID is null");
        }

        if (!cabRepo.findById(cabId).isPresent()) {
            throw new UnknownCabIdException();
        }

        boolean complete = stream(repo.findAll()).noneMatch(pickup -> cabId.equals(pickup.getCabId()));

        return PickupCompleteModel.builder()
                .complete(complete)
                .build();
    }

    public void acceptPickup(Long customerId) {
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID is null");
        }

        Optional<JobEntity> jobOptional = jobService.getJob(customerId);
        if (!jobOptional.isPresent()) {
            throw new UnknownJobIdException();
        }

        JobEntity job = jobOptional.get();
        job.setCustomerState(IN_CAB);
        jobService.updateJob(job);

        repo.deleteById(customerId);
    }
}
