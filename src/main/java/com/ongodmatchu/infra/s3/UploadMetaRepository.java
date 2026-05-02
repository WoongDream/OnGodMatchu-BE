package com.ongodmatchu.infra.s3;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadMetaRepository extends JpaRepository<UploadMeta, Long> {

  Optional<UploadMeta> findByS3Key(String s3Key);
}
