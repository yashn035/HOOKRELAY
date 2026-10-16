package com.hookrelay.api.service;

import com.hookrelay.api.dto.ProjectDtos.CreateProjectRequest;
import com.hookrelay.api.dto.ProjectDtos.ProjectResponse;
import com.hookrelay.core.entity.Org;
import com.hookrelay.core.entity.Project;
import com.hookrelay.core.repository.OrgRepository;
import com.hookrelay.core.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private OrgRepository orgRepository;

    @InjectMocks
    private ProjectService projectService;

    private Org testOrg;
    private Project testProject;
    private final UUID orgId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        testOrg = Org.builder().name("Test Org").slug("test-org").build();
        testOrg.setId(orgId);

        testProject = Project.builder()
                .org(testOrg)
                .name("Default Project")
                .rateLimitPerSecond(150)
                .build();
        testProject.setId(projectId);
    }

    @Test
    @DisplayName("Should list projects scoped to organization")
    void testGetProjectsForOrg() {
        when(projectRepository.findByOrgId(orgId)).thenReturn(List.of(testProject));

        List<ProjectResponse> list = projectService.getProjectsForOrg(orgId);

        assertEquals(1, list.size());
        assertEquals("Default Project", list.get(0).name());
        assertEquals(150, list.get(0).rateLimitPerSecond());
    }

    @Test
    @DisplayName("Should create project and bind to user organization")
    void testCreateProject() {
        CreateProjectRequest req = new CreateProjectRequest("Staging", "Staging cluster", 200);

        when(orgRepository.findById(orgId)).thenReturn(Optional.of(testOrg));
        when(projectRepository.save(any(Project.class))).thenAnswer(i -> {
            Project p = i.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        ProjectResponse res = projectService.createProject(orgId, req);

        assertNotNull(res);
        assertEquals("Staging", res.name());
        assertEquals(200, res.rateLimitPerSecond());
    }
}
