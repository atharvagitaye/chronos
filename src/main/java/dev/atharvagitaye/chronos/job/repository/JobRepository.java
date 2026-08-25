package dev.atharvagitaye.chronos.job.repository;

import dev.atharvagitaye.chronos.job.entity.Job;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRepository extends JpaRepository<Job, UUID> {
}