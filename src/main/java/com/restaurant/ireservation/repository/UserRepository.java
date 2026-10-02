package com.restaurant.ireservation.repository;

import com.restaurant.ireservation.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    String USERS_BY_USERNAME_CACHE = "usersByUsername";
    String USERS_BY_EMAIL_CACHE = "usersByEmail";

    Optional<User> findUserByUsername(String username);

    Optional<User> findOneByEmail(String email);

    @Modifying
    @Query(value = "update User u set u.sessionId = :sessionId where u.id = :userId")
    int updateSessionIdById(@Param("userId") Long userId, @Param("sessionId") String sessionId);

    @Modifying
    @Query(value = "update User u set u.refreshToken = :refresh where u.id = :userId")
    int updateRefreshTokenById(@Param("userId") Long userId, @Param("refresh") String token);

    @Query(value = "select u from User u left join fetch u.role r left join fetch r.permissions p where u.username = :username")
    Optional<User> findOneWithAuthoritiesByUsername(@Param("username") String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    @Query(value = "select u.refreshToken from User u where u.id = :userId")
    String getRefreshTokenById(@Param("userId") Long userId);

    @Query(value = "update User u set u.refreshToken = null where u.username = :username")
    @Modifying
    int resetRefreshTokenByUsername(@Param("username") String username);

    @Query("update User u set u.avatar = :fileName where u.id = :id")
    @Modifying
    int updateUserAvatarById(@Param("id") Long id, @Param("fileName") String fileName);

}
