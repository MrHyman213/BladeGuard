const tg = window.Telegram.WebApp;
tg.expand();

const token = new URLSearchParams(window.location.search).get('token');
let submissionData = null;

async function loadSubmission() {
    try {
        const response = await fetch(`/api/moderation/submission/${token}`);
        if (!response.ok) throw new Error('Failed to load');
        
        submissionData = await response.json();
        
        document.getElementById('photo').src = submissionData.photoUrl;
        document.getElementById('modelName').value = submissionData.modelName || '';
        document.getElementById('description').value = submissionData.description || '';
        
        renderAlternativeModels(submissionData.alternativeModels || []);
        
    } catch (error) {
        tg.showAlert('Ошибка загрузки данных');
        tg.close();
    }
}

function renderAlternativeModels(models) {
    const container = document.getElementById('alternativeModels');
    container.innerHTML = '';
    
    models.forEach((model, index) => {
        const div = document.createElement('div');
        div.className = 'model-item';
        div.innerHTML = `
            <input type="text" class="form-control" value="${model}" data-index="${index}">
            <button class="btn-remove" data-index="${index}">×</button>
        `;
        container.appendChild(div);
    });
}

document.getElementById('addModel').addEventListener('click', () => {
    const models = getAlternativeModels();
    models.push('');
    renderAlternativeModels(models);
});

document.addEventListener('click', (e) => {
    if (e.target.classList.contains('btn-remove')) {
        const index = parseInt(e.target.dataset.index);
        const models = getAlternativeModels();
        models.splice(index, 1);
        renderAlternativeModels(models);
    }
});

function getAlternativeModels() {
    const inputs = document.querySelectorAll('#alternativeModels input');
    return Array.from(inputs).map(input => input.value.trim()).filter(v => v);
}

async function saveSubmission() {
    const data = {
        modelName: document.getElementById('modelName').value,
        description: document.getElementById('description').value,
        alternativeModels: getAlternativeModels()
    };
    
    const response = await fetch(`/api/moderation/submission/${token}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data)
    });
    
    if (!response.ok) throw new Error('Failed to save');
}

document.getElementById('saveAndApprove').addEventListener('click', async () => {
    try {
        await saveSubmission();
        const response = await fetch(`/api/moderation/submission/${token}/approve`, {
            method: 'POST'
        });
        
        if (response.ok) {
            tg.showAlert('Заявка одобрена!');
            tg.close();
        }
    } catch (error) {
        tg.showAlert('Ошибка при одобрении');
    }
});

document.getElementById('saveDraft').addEventListener('click', async () => {
    try {
        await saveSubmission();
        tg.showAlert('Изменения сохранены');
    } catch (error) {
        tg.showAlert('Ошибка при сохранении');
    }
});

document.getElementById('reject').addEventListener('click', async () => {
    const reason = prompt('Укажите причину отклонения:');
    if (!reason) return;
    
    try {
        const response = await fetch(`/api/moderation/submission/${token}/reject`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ reason })
        });
        
        if (response.ok) {
            tg.showAlert('Заявка отклонена');
            tg.close();
        }
    } catch (error) {
        tg.showAlert('Ошибка при отклонении');
    }
});

loadSubmission();
