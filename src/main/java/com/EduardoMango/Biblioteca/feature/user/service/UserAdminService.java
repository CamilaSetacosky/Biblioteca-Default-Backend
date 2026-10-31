package com.EduardoMango.Biblioteca.feature.user.service;

import com.EduardoMango.Biblioteca.exception.BusinessRuleException;
import com.EduardoMango.Biblioteca.exception.ResourceNotFoundException;
import com.EduardoMango.Biblioteca.feature.loan.domain.LoanStatus;
import com.EduardoMango.Biblioteca.feature.loan.repository.LoanRepository;
import com.EduardoMango.Biblioteca.feature.user.dto.UserAdminResponse;
import com.EduardoMango.Biblioteca.feature.user.mapper.UserAdminMapper;
import com.EduardoMango.Biblioteca.feature.auth.domain.CredentialsEntity;
import com.EduardoMango.Biblioteca.feature.auth.domain.RoleEntity;
import com.EduardoMango.Biblioteca.feature.user.UserEntity;
import com.EduardoMango.Biblioteca.feature.auth.domain.Roles;
import com.EduardoMango.Biblioteca.feature.auth.repository.CredentialsRepository;
import com.EduardoMango.Biblioteca.feature.auth.repository.RoleRepository;
import com.EduardoMango.Biblioteca.feature.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserAdminService {

    private final UserRepository userRepository;
    private final CredentialsRepository credentialsRepository;
    private final RoleRepository roleRepository;
    private final LoanRepository loanRepository;
    private final UserAdminMapper userAdminMapper;

    public Page<UserAdminResponse> getUsers(int page, int size, String sort) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("La página debe ser mayor o igual a 0 y el tamaño debe estar entre 1 y 100");
        }

        Sort requestedSort = parseSort(sort);
        PageRequest pageRequest = PageRequest.of(page, size, requestedSort);
        return userRepository.findAll(pageRequest).map(userAdminMapper::toUserAdminResponse);
    }

    private Sort parseSort(String sort) {
        String[] parts = sort.split(",", -1);
        Set<String> allowedFields = Set.of("publicId", "nombre", "apellido", "email", "rol", "activo");

        if (parts.length != 2 || !allowedFields.contains(parts[0])) {
            throw new IllegalArgumentException("Orden inválido. Usá campo,asc o campo,desc con publicId, nombre, apellido, email, rol o activo");
        }

        String direction = parts[1];
        if (!"asc".equalsIgnoreCase(direction) && !"desc".equalsIgnoreCase(direction)) {
            throw new IllegalArgumentException("La dirección de orden debe ser asc o desc");
        }

        Sort requestedSort = Sort.by(Sort.Direction.fromString(direction), parts[0]);
        return "publicId".equals(parts[0])
                ? requestedSort
                : requestedSort.and(Sort.by("publicId"));
    }

    @Transactional
    public UserAdminResponse updateUserStatus(UUID targetUserPublicId, Boolean activo, String authenticatedPrincipalName) {
        UserEntity targetUser = userRepository.findByPublicId(targetUserPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con publicId: " + targetUserPublicId));

        UserEntity authenticatedUser = findUserByPrincipal(authenticatedPrincipalName);

        if (targetUser.getPublicId().equals(authenticatedUser.getPublicId()) && Boolean.FALSE.equals(activo)) {
            throw new BusinessRuleException("Operación no permitida: Un usuario no puede desactivar su propia cuenta");
        }

        if (Boolean.FALSE.equals(activo)) {
            boolean hasPendingLoans = loanRepository.existsByUsuarioAndEstadoIn(
                    targetUser, List.of(LoanStatus.PRESTADO, LoanStatus.CON_RETRASO));
            if (hasPendingLoans) {
                throw new BusinessRuleException("No se puede desactivar un usuario con préstamos pendientes de devolución");
            }
        }

        targetUser.setActivo(activo);
        userRepository.save(targetUser);

        credentialsRepository.findByUsuario(targetUser).ifPresent(credentials -> {
            credentials.setEnabled(activo);
            credentialsRepository.save(credentials);
        });

        return userAdminMapper.toUserAdminResponse(targetUser);
    }

    @Transactional
    public UserAdminResponse updateUserRole(UUID targetUserPublicId, String nuevoRol, String authenticatedPrincipalName) {
        UserEntity targetUser = userRepository.findByPublicId(targetUserPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con publicId: " + targetUserPublicId));

        UserEntity authenticatedUser = findUserByPrincipal(authenticatedPrincipalName);

        if (targetUser.getPublicId().equals(authenticatedUser.getPublicId())) {
            throw new BusinessRuleException("Operación no permitida: Un usuario no puede modificar su propio rol");
        }

        String normalizedRole = nuevoRol.trim().toUpperCase();
        if (normalizedRole.startsWith("ROLE_")) {
            normalizedRole = normalizedRole.substring(5);
        }

        if (!"SOCIO".equals(normalizedRole) && !"BIBLIOTECARIO".equals(normalizedRole)) {
            throw new BusinessRuleException("Rol no permitido: " + nuevoRol + ". Solo se permiten transiciones entre SOCIO y BIBLIOTECARIO");
        }

        targetUser.setRol(normalizedRole);
        userRepository.save(targetUser);

        Roles roleEnum = "BIBLIOTECARIO".equals(normalizedRole) ? Roles.ROLE_BIBLIOTECARIO : Roles.ROLE_SOCIO;
        RoleEntity roleEntity = roleRepository.findByRole(roleEnum)
                .orElseGet(() -> roleRepository.save(new RoleEntity(roleEnum)));

        credentialsRepository.findByUsuario(targetUser).ifPresent(credentials -> {
            Set<RoleEntity> updatedRoles = new HashSet<>();
            updatedRoles.add(roleEntity);
            credentials.setRoles(updatedRoles);
            credentialsRepository.save(credentials);
        });

        return userAdminMapper.toUserAdminResponse(targetUser);
    }

    private UserEntity findUserByPrincipal(String principalName) {
        return credentialsRepository.findByUsername(principalName)
                .map(CredentialsEntity::getUsuario)
                .or(() -> userRepository.findByEmail(principalName))
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + principalName));
    }
}

