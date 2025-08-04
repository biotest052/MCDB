async function generatePost(postObj) {
    const userId = getSignedInId();
    const pfp = await getProfilePictureLink(postObj.userId);

    let ret = `
    <div class="post">
    <img src="${pfp !== null ? pfp : "defaultProfilePic.png"}" class="post-avatar"/>
    <div class="post-info">
    <a href="profile.html?id=${postObj.userId}">
    <h3>${postObj.author}</h3>
    </a>
    </div>
    <div class="post-time">${postObj.timestamp}</div>
    <div class="post-content">
    ${postObj.content}
    </div>
    `;

    if (postObj.fileLink !== undefined && postObj.fileType !== undefined) {
        ret += `<div class="post-file">`;
        ret += generateFileView(postObj.fileType, postObj.fileLink);
        ret += `</div>`;
    }

    if (postObj.userId === userId) {
        ret += `<p id="loadingMessage${postObj.id}" style="margin-top: 5px; margin-bottom: 5px;"></p>`;
        ret += `<button style="margin-top: 5px;" type="button" class="btn btn-secondary" onclick="deletePost(${postObj.id})">Delete Post</button>`;
    }

    ret += `<a class="expand-link" href="post.html?id=${postObj.id}">EXPAND</a>`;
    ret += `</div>`;

    return ret;
}

function generateFileView(fileType, fileLink) {
    const type = fileType.split("/")[0];
    if (type === 'video') {
        return `<video width="100%" height="auto" controls>
        <source src="${fileLink}" type="${fileType}">
        Your browser does not support the video tag.
        </video>`;
    } else if (type === "image") {
        return `<a href="${fileLink}" target="_blank" rel="noopener noreferrer">
        <img width="100%" height="auto" src="${fileLink}" alt="Image"/>
        </a>`;
    } else {
        const fileName = fileLink.split('/').pop().split('?')[0];
        return `<a href="${fileLink}" target="_blank" rel="noopener noreferrer">
            Download ${fileName} (${fileType})
            </a>`;
    }
}
