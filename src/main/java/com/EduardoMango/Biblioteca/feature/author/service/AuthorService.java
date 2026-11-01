package com.EduardoMango.Biblioteca.feature.author.service;

import com.EduardoMango.Biblioteca.exception.BusinessRuleException;
import com.EduardoMango.Biblioteca.exception.ConflictException;
import com.EduardoMango.Biblioteca.feature.author.AuthorIdentity;
import com.EduardoMango.Biblioteca.exception.ResourceNotFoundException;
import com.EduardoMango.Biblioteca.feature.author.Author;
import com.EduardoMango.Biblioteca.feature.author.dto.AuthorRequest;
import com.EduardoMango.Biblioteca.feature.author.dto.AuthorResponse;
import com.EduardoMango.Biblioteca.feature.author.AuthorMapper;
import com.EduardoMango.Biblioteca.feature.author.AuthorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthorService {

    private final AuthorRepository authorRepository;
    private final AuthorMapper authorMapper;
    private final AuthorDeletionValidator authorDeletionValidator;

    @Transactional
    public AuthorResponse createAuthor(AuthorRequest request) {
        rejectDuplicate(request, null);
        Author author = authorMapper.toEntity(request);
        if (author.getPublicId() == null) {
            author.setPublicId(UUID.randomUUID());
        }
        Author saved = authorRepository.saveAndFlush(author);
        return authorMapper.toResponse(saved);
    }

    public List<AuthorResponse> getAuthors(String q, String nacionalidad) {
        String trimmedQ = (q != null && !q.isBlank()) ? q.trim() : null;
        String trimmedNac = (nacionalidad != null && !nacionalidad.isBlank()) ? nacionalidad.trim() : null;
        return authorRepository.searchAuthors(trimmedQ, trimmedNac)
                .stream()
                .map(authorMapper::toResponse)
                .toList();
    }

    public AuthorResponse getAuthorByPublicId(UUID publicId) {
        return authorRepository.findByPublicId(publicId)
                .map(authorMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Autor no encontrado con publicId: " + publicId));
    }

    @Transactional
    public AuthorResponse updateAuthor(UUID publicId, AuthorRequest request) {
        Author author = authorRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Autor no encontrado con publicId: " + publicId));

        rejectDuplicate(request, publicId);
        authorMapper.updateEntityFromRequest(request, author);
        Author updated = authorRepository.saveAndFlush(author);
        return authorMapper.toResponse(updated);
    }

    private void rejectDuplicate(AuthorRequest request, UUID currentPublicId) {
        String requestedKey = AuthorIdentity.key(request.nombre(), request.apellido(), request.fechaNacimiento());
        boolean duplicate = authorRepository.findAllByFechaNacimiento(request.fechaNacimiento()).stream()
                .filter(author -> !author.getPublicId().equals(currentPublicId))
                .anyMatch(author -> requestedKey.equals(AuthorIdentity.key(
                        author.getNombre(), author.getApellido(), author.getFechaNacimiento())));

        if (duplicate) {
            throw new ConflictException(AuthorIdentity.DUPLICATE_MESSAGE);
        }
    }

    @Transactional
    public void deleteAuthor(UUID publicId) {
        Author author = authorRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Autor no encontrado con publicId: " + publicId));

        if (authorDeletionValidator.hasAssociatedBooks(publicId)) {
            throw new BusinessRuleException("El autor no puede ser eliminado por tener obras asociadas");
        }

        authorRepository.delete(author);
    }
}

